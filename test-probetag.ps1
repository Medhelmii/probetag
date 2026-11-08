# =====================================================================
#  Probetag - kompletter API-Test (Schritte 1 bis 7)
#  Startet bei einer LEEREN Datenbank und legt alle Testdaten selbst an.
#  Kann beliebig oft ausgefuehrt werden (jede Ausfuehrung nutzt neue E-Mails).
#
#  Ausfuehren im IntelliJ-Terminal (PowerShell), im Projektordner:
#     powershell -ExecutionPolicy Bypass -File .\test-probetag.ps1
#
#  Optionen:
#     -SkipRaceTest   Ueberbuchungstest mit 5 parallelen Anfragen ueberspringen
#     -WithReminder   Erinnerungs-Mail testen (nur wenn app.reminder.cron = "0 * * * * *")
#     -Base http://localhost:8080   anderer Port
# =====================================================================

param(
    [string]$Base = "http://localhost:8081",
    [string]$MailpitUrl = "http://localhost:8025",
    [string]$DbContainer = "probetag-db",
    [string]$AdminEmail = "admin@probetag.de",
    [string]$AdminPassword = "admin12345",
    [switch]$SkipRaceTest,
    [switch]$WithReminder
)

$api = "$Base/api/v1"
$run = Get-Date -Format "MMddHHmmss"     # macht alle E-Mails dieser Ausfuehrung eindeutig
$script:passed = 0
$script:failed = 0
$script:failures = @()

# ---------------------------------------------------------------------
#  Hilfsfunktionen
# ---------------------------------------------------------------------

# Windows PowerShell 5.1 behandelt manche JSON-Typen (z. B. application/vnd.spring-boot.actuator.v3+json)
# als Binaerdaten. Deshalb lesen wir den Inhalt immer selbst als UTF-8-Text.
function Get-ResponseText($response) {
    if ($response.RawContentStream) {
        return [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    }
    return [string]$response.Content
}

function Invoke-Api {
    param([string]$Method = "GET", [string]$Path, $Body = $null, $Headers = @{})

    $params = @{ Uri = "$api$Path"; Method = $Method; Headers = $Headers; UseBasicParsing = $true; ErrorAction = "Stop" }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 5
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $params.ContentType = "application/json; charset=utf-8"
    }

    try {
        $response = Invoke-WebRequest @params
        $data = $null
        $text = Get-ResponseText $response
        if ($text) { $data = $text | ConvertFrom-Json }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data; Raw = $text }
    }
    catch {
        $err = $_
        $httpResponse = $err.Exception.Response
        if ($null -eq $httpResponse) {
            throw "Keine Verbindung zu $api$Path : $($err.Exception.Message)"
        }
        $data = $null
        $raw = $null
        if ($err.ErrorDetails -and $err.ErrorDetails.Message) {
            $raw = $err.ErrorDetails.Message
        }
        # PowerShell 5.1 fuellt ErrorDetails nicht bei "application/problem+json" -> Antwort selbst lesen
        if (-not $raw -and $httpResponse.PSObject.Methods['GetResponseStream']) {
            try {
                $stream = $httpResponse.GetResponseStream()
                if ($stream.CanSeek) { $stream.Position = 0 }
                $raw = (New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)).ReadToEnd()
            } catch { $raw = $null }
        }
        if ($raw) {
            try { $data = $raw | ConvertFrom-Json } catch { $data = $null }
        }
        return [pscustomobject]@{ Status = [int]$httpResponse.StatusCode; Data = $data; Raw = $raw }
    }
}

function Get-Detail($response) {
    if ($null -ne $response.Data -and $response.Data.PSObject.Properties['detail']) { return $response.Data.detail }
    return ""
}

function Write-Section($title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Cyan
}

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")
    if ($Condition) {
        Write-Host "  [OK]   $Name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "  [FAIL] $Name $Detail" -ForegroundColor Red
        $script:failed++
        $script:failures += $Name
    }
}

function Assert-Status {
    param([string]$Name, $Response, [int]$Expected)
    Assert-True $Name ($Response.Status -eq $Expected) "(erwartet $Expected, bekommen $($Response.Status) $(Get-Detail $Response))"
}

function Get-AuthHeader($token) { return @{ Authorization = "Bearer $token" } }

function Format-Utc([datetime]$date) { return $date.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'") }

function New-TestParticipant($name) {
    $email = "$name.$run@test.de"
    $r = Invoke-Api POST "/auth/register/participant" @{ email = $email; password = "geheim123"; firstName = $name; lastName = "Test" }
    if ($r.Status -ne 201) { throw "Registrierung von $name fehlgeschlagen: $($r.Status) $(Get-Detail $r)" }
    return [pscustomobject]@{ Name = $name; Email = $email; Headers = (Get-AuthHeader $r.Data.token); UserId = $r.Data.userId }
}

function New-SlotBody([datetime]$start, [int]$capacity) {
    return @{
        jobTitle    = "Elektriker/in"
        description = "Ein Tag auf der Baustelle: Kabel verlegen, Steckdosen montieren."
        startsAt    = (Format-Utc $start)
        endsAt      = (Format-Utc $start.AddHours(7))
        capacity    = $capacity
        dressCode   = "Feste Schuhe"
    }
}

function New-TestSlot([double]$minutesFromNow, [int]$capacity) {
    $r = Invoke-Api POST "/slots" (New-SlotBody (Get-Date).AddMinutes($minutesFromNow) $capacity) $script:companyHeaders
    if ($r.Status -ne 201) { throw "Termin anlegen fehlgeschlagen: $($r.Status) $(Get-Detail $r)" }
    return $r.Data
}

function Test-ContainsSlot($list, $slotId) {
    return @(@($list) | Where-Object { $_ -and $_.id -eq $slotId }).Count -gt 0
}

function Invoke-Sql($sql) {
    $output = docker exec $DbContainer psql -U probetag -d probetag -c $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "SQL fehlgeschlagen: $sql`n$output" }
}

function Move-SlotsToPast($slotIds, [string]$interval) {
    $list = (@($slotIds) | ForEach-Object { "'$_'" }) -join ","
    Invoke-Sql "UPDATE slots SET starts_at = now() - interval '$interval' WHERE id IN ($list);"
}

function Clear-Mailpit {
    try { Invoke-WebRequest -Uri "$MailpitUrl/api/v1/messages" -Method Delete -UseBasicParsing -ErrorAction Stop | Out-Null; return $true }
    catch { return $false }
}

function Get-Mails {
    try {
        $r = Invoke-WebRequest -Uri "$MailpitUrl/api/v1/messages?limit=500" -UseBasicParsing -ErrorAction Stop
        return @(((Get-ResponseText $r) | ConvertFrom-Json).messages)
    } catch { return @() }
}

function Find-Mail($to, $subjectPart) {
    return @(Get-Mails | Where-Object {
        $mail = $_
        @($mail.To | Where-Object { $_.Address -eq $to }).Count -gt 0 -and $mail.Subject -like "*$subjectPart*"
    })
}

function Assert-Mail {
    param([string]$Name, [string]$To, [string]$SubjectPart, [int]$TimeoutSeconds = 6)
    if (-not $script:mailpitOk) { Write-Host "  [SKIP] $Name (Mailpit nicht erreichbar)" -ForegroundColor Yellow; return }
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $found = $false
    while (-not $found -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $found = @(Find-Mail $To $SubjectPart).Count -gt 0
    }
    $total = @(Get-Mails).Count
    Assert-True $Name $found "(keine Mail an $To mit '$SubjectPart'; Mailpit hat insgesamt $total Mails)"
}

# ---------------------------------------------------------------------
#  0. Vorbereitung
# ---------------------------------------------------------------------

Write-Host "Probetag API-Test  |  $api  |  Lauf $run" -ForegroundColor White

Write-Section "Schritt 1: Backend, Datenbank, Mailpit"
try {
    $health = Invoke-WebRequest -Uri "$Base/actuator/health" -UseBasicParsing -ErrorAction Stop
    Assert-True "Backend laeuft (/actuator/health = UP)" (((Get-ResponseText $health) | ConvertFrom-Json).status -eq "UP")
} catch {
    Write-Host "  [FAIL] Backend nicht erreichbar unter $Base. Laeuft die App? Stimmt der Port?" -ForegroundColor Red
    exit 1
}

docker exec $DbContainer psql -U probetag -d probetag -c "SELECT 1;" 2>&1 | Out-Null
Assert-True "Datenbank-Container '$DbContainer' erreichbar" ($LASTEXITCODE -eq 0)

$script:mailpitOk = Clear-Mailpit
Assert-True "Mailpit erreichbar und geleert" $script:mailpitOk

# ---------------------------------------------------------------------
#  Schritt 2: Registrierung, Login, JWT
# ---------------------------------------------------------------------

Write-Section "Schritt 2: Registrierung, Login, JWT"

$companyEmail = "firma.$run@test.de"
$r = Invoke-Api POST "/auth/register/company" @{
    email = $companyEmail; password = "geheim123"; name = "Elektro Mueller $run"; sector = "Handwerk"
    address = "Hauptstr. 1"; city = "Freiburg"; postalCode = "79098"; description = "Elektrobetrieb"
}
Assert-Status "Unternehmen registrieren -> 201" $r 201
Assert-True "Rolle ist COMPANY" ($r.Data.role -eq "COMPANY")
$script:companyHeaders = Get-AuthHeader $r.Data.token
$companyUserId = $r.Data.userId

$anna = New-TestParticipant "anna"
Assert-True "Teilnehmerin anna registriert" ($null -ne $anna.UserId)
Assert-Mail "Willkommens-Mail an anna" $anna.Email "Willkommen"

$r = Invoke-Api POST "/auth/register/participant" @{ email = $anna.Email; password = "geheim123"; firstName = "Anna"; lastName = "Doppelt" }
Assert-Status "Gleiche E-Mail nochmal -> 409" $r 409

$r = Invoke-Api POST "/auth/register/participant" @{ email = "kurz.$run@test.de"; password = "123"; firstName = "Kurz"; lastName = "Test" }
Assert-Status "Passwort zu kurz -> 400" $r 400
Assert-True "Fehler nennt das Feld 'password'" ($null -ne $r.Data -and $null -ne $r.Data.errors -and $null -ne $r.Data.errors.password) "(Antwort war: $($r.Raw))"

$r = Invoke-Api POST "/auth/register/company" @{
    email = "plz.$run@test.de"; password = "geheim123"; name = "PLZ Test"; sector = "Handwerk"
    address = "Weg 1"; city = "Freiburg"; postalCode = "ABC"
}
Assert-Status "Ungueltige Postleitzahl -> 400" $r 400

$r = Invoke-Api POST "/auth/login" @{ email = $anna.Email.ToUpper(); password = "geheim123" }
Assert-Status "Login (E-Mail in Grossbuchstaben) -> 200" $r 200

$r = Invoke-Api POST "/auth/login" @{ email = $anna.Email; password = "falsch123" }
Assert-Status "Falsches Passwort -> 401" $r 401

$r = Invoke-Api POST "/auth/login" @{ email = "gibtsnicht.$run@test.de"; password = "geheim123" }
Assert-Status "Unbekannte E-Mail -> 401" $r 401

$r = Invoke-Api GET "/me" $null $anna.Headers
Assert-Status "/me mit Token -> 200" $r 200
Assert-True "/me liefert annas E-Mail" ($r.Data.email -eq $anna.Email)

$r = Invoke-Api GET "/me"
Assert-Status "/me ohne Token -> 401" $r 401

$r = Invoke-Api GET "/me" $null @{ Authorization = "Bearer kaputt.token.123" }
Assert-Status "/me mit gefaelschtem Token -> 401" $r 401

$r = Invoke-Api POST "/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
Assert-Status "Admin-Login -> 200" $r 200
if ($r.Status -ne 200) {
    Write-Host "  Admin-Login fehlgeschlagen. Stehen app.admin.email/password in application-local.yml?" -ForegroundColor Red
    exit 1
}
Assert-True "Rolle ist ADMIN" ($r.Data.role -eq "ADMIN")
$adminHeaders = Get-AuthHeader $r.Data.token
$adminUserId = $r.Data.userId

# ---------------------------------------------------------------------
#  Schritt 7a: Freischaltung (vor den Termin-Tests noetig)
# ---------------------------------------------------------------------

Write-Section "Schritt 7a: Freischaltung von Unternehmen"

$slot0 = New-TestSlot (7 * 24 * 60) 3
Assert-True "Nicht freigeschaltetes Unternehmen kann Termin anlegen" ($null -ne $slot0.id)

$r = Invoke-Api GET "/slots"
Assert-True "Termin ist vor Freischaltung NICHT in der Suche" (-not (Test-ContainsSlot $r.Data $slot0.id))

$r = Invoke-Api GET "/slots/$($slot0.id)"
Assert-Status "Termindetail vor Freischaltung -> 404" $r 404

$r = Invoke-Api POST "/slots/$($slot0.id)/bookings" $null $anna.Headers
Assert-Status "Buchen vor Freischaltung -> 404" $r 404

$r = Invoke-Api GET "/admin/companies" $null $anna.Headers
Assert-Status "Teilnehmerin im Admin-Bereich -> 403" $r 403

$r = Invoke-Api GET "/admin/companies"
Assert-Status "Admin-Bereich ohne Token -> 401" $r 401

$r = Invoke-Api GET "/admin/companies" $null $adminHeaders
Assert-Status "Admin: wartende Unternehmen -> 200" $r 200
$pendingCompany = @($r.Data) | Where-Object { $_.email -eq $companyEmail } | Select-Object -First 1
Assert-True "Unser Unternehmen wartet auf Freischaltung" ($null -ne $pendingCompany -and -not $pendingCompany.verified)
$companyId = $pendingCompany.id

$r = Invoke-Api POST "/admin/companies/$companyId/verify" $null $adminHeaders
Assert-Status "Admin schaltet frei -> 200" $r 200
Assert-True "verified = true" ($r.Data.verified -eq $true)
Assert-Mail "Freischaltungs-Mail an das Unternehmen" $companyEmail "freigeschaltet"

$r = Invoke-Api POST "/admin/companies/$companyId/verify" $null $adminHeaders
Assert-Status "Nochmal freischalten -> 409" $r 409

$r = Invoke-Api GET "/slots"
Assert-True "Termin ist nach Freischaltung in der Suche" (Test-ContainsSlot $r.Data $slot0.id)

# ---------------------------------------------------------------------
#  Schritt 3: Termine
# ---------------------------------------------------------------------

Write-Section "Schritt 3: Termine"

$r = Invoke-Api GET "/slots/$($slot0.id)"
Assert-Status "Termindetail oeffentlich -> 200" $r 200
Assert-True "Termindetail enthaelt den Firmennamen" ($r.Data.companyName -eq "Elektro Mueller $run")

$r = Invoke-Api GET "/slots?city=freiburg"
Assert-True "Suche city=freiburg (klein) findet den Termin" (Test-ContainsSlot $r.Data $slot0.id)

$r = Invoke-Api GET "/slots?city=Berlin"
Assert-True "Suche city=Berlin findet ihn nicht" (-not (Test-ContainsSlot $r.Data $slot0.id))

$r = Invoke-Api GET "/slots?sector=handwerk"
Assert-True "Suche sector=handwerk findet den Termin" (Test-ContainsSlot $r.Data $slot0.id)

$update = New-SlotBody (Get-Date).AddDays(8) 5
$r = Invoke-Api PUT "/slots/$($slot0.id)" $update $script:companyHeaders
Assert-Status "Termin aendern -> 200" $r 200
Assert-True "Kapazitaet ist jetzt 5" ($r.Data.capacity -eq 5)

$r = Invoke-Api GET "/companies/me/slots" $null $script:companyHeaders
Assert-True "Eigene Termine enthalten den Termin" (Test-ContainsSlot $r.Data $slot0.id)

$r = Invoke-Api POST "/slots" (New-SlotBody (Get-Date).AddDays(7) 2) $anna.Headers
Assert-Status "Teilnehmerin legt Termin an -> 403" $r 403

$bad = New-SlotBody (Get-Date).AddDays(7) 2
$bad.endsAt = Format-Utc (Get-Date).AddDays(6)
$r = Invoke-Api POST "/slots" $bad $script:companyHeaders
Assert-Status "Ende vor Beginn -> 400" $r 400

$r = Invoke-Api POST "/slots" (New-SlotBody (Get-Date).AddDays(-1) 2) $script:companyHeaders
Assert-Status "Beginn in der Vergangenheit -> 400" $r 400

$r = Invoke-Api POST "/slots" (New-SlotBody (Get-Date).AddDays(7) 21) $script:companyHeaders
Assert-Status "Kapazitaet 21 -> 400" $r 400

$r = Invoke-Api POST "/auth/register/company" @{
    email = "konkurrenz.$run@test.de"; password = "geheim123"; name = "Konkurrenz GmbH"; sector = "Handwerk"
    address = "Nebenstr. 2"; city = "Freiburg"; postalCode = "79100"
}
$otherCompanyHeaders = Get-AuthHeader $r.Data.token
$r = Invoke-Api PUT "/slots/$($slot0.id)" $update $otherCompanyHeaders
Assert-Status "Fremdes Unternehmen aendert Termin -> 403" $r 403

$r = Invoke-Api GET "/companies/me/slots"
Assert-Status "Eigene Termine ohne Token -> 401" $r 401

$r = Invoke-Api POST "/slots/$($slot0.id)/cancel" $null $script:companyHeaders
Assert-Status "Termin absagen -> 200" $r 200
Assert-True "Status ist CANCELLED" ($r.Data.status -eq "CANCELLED")

$r = Invoke-Api GET "/slots"
Assert-True "Abgesagter Termin ist nicht mehr in der Suche" (-not (Test-ContainsSlot $r.Data $slot0.id))

$r = Invoke-Api PUT "/slots/$($slot0.id)" $update $script:companyHeaders
Assert-Status "Abgesagten Termin aendern -> 400" $r 400

# ---------------------------------------------------------------------
#  Schritt 4 + 5: Buchungen und E-Mails
# ---------------------------------------------------------------------

Write-Section "Schritt 4 + 5: Buchungen und E-Mails"

$ben = New-TestParticipant "ben"
$carla = New-TestParticipant "carla"
$slot1 = New-TestSlot (7 * 24 * 60) 2

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $anna.Headers
Assert-Status "anna bucht -> 201" $r 201
Assert-True "Status CONFIRMED" ($r.Data.status -eq "CONFIRMED")
$annaBooking1 = $r.Data
Assert-Mail "Buchungsbestaetigung an anna" $anna.Email "Buchung best"
Assert-Mail "'Neue Buchung' an das Unternehmen" $companyEmail "Neue Buchung"

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $anna.Headers
Assert-Status "anna bucht nochmal -> 409" $r 409

$r = Invoke-Api GET "/participants/me/bookings" $null $anna.Headers
Assert-True "annas Buchungen enthalten den Termin" (@(@($r.Data) | Where-Object { $_.slotId -eq $slot1.id }).Count -eq 1)

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $ben.Headers
Assert-Status "ben bucht den letzten Platz -> 201" $r 201
$benBooking1 = $r.Data

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $carla.Headers
Assert-Status "carla: ausgebucht -> 409" $r 409

$r = Invoke-Api GET "/companies/me/slots/$($slot1.id)/bookings" $null $script:companyHeaders
Assert-True "Teilnehmerliste hat 2 Eintraege" (@($r.Data).Count -eq 2)
Assert-True "Teilnehmerliste enthaelt E-Mails" (@(@($r.Data) | Where-Object { $_.email -eq $anna.Email }).Count -eq 1)

$r = Invoke-Api GET "/companies/me/slots/$($slot1.id)/bookings" $null $otherCompanyHeaders
Assert-Status "Fremdes Unternehmen sieht Teilnehmerliste -> 403" $r 403

$r = Invoke-Api POST "/bookings/$($annaBooking1.id)/cancel" $null $ben.Headers
Assert-Status "ben storniert annas Buchung -> 403" $r 403

$r = Invoke-Api POST "/bookings/$($annaBooking1.id)/cancel" $null $anna.Headers
Assert-Status "anna storniert -> 200" $r 200
Assert-True "Status CANCELLED" ($r.Data.status -eq "CANCELLED")
Assert-Mail "Stornierungs-Mail an das Unternehmen" $companyEmail "Stornierung"

$r = Invoke-Api POST "/bookings/$($annaBooking1.id)/cancel" $null $anna.Headers
Assert-Status "Nochmal stornieren -> 409" $r 409

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $carla.Headers
Assert-Status "carla rueckt nach -> 201" $r 201

$r = Invoke-Api POST "/slots/$($slot1.id)/bookings" $null $script:companyHeaders
Assert-Status "Unternehmen bucht -> 403" $r 403

$r = Invoke-Api POST "/bookings/$($benBooking1.id)/attendance" @{ attended = $true } $script:companyHeaders
Assert-Status "Anwesenheit vor Beginn -> 400" $r 400

$slot2 = New-TestSlot 120 5
$r = Invoke-Api POST "/slots/$($slot2.id)/bookings" $null $anna.Headers
Assert-Status "anna bucht Termin in 2 Stunden -> 201" $r 201
$r = Invoke-Api POST "/bookings/$($r.Data.id)/cancel" $null $anna.Headers
Assert-Status "Stornieren weniger als 24h vorher -> 400" $r 400

$r = Invoke-Api POST "/slots/$($slot0.id)/bookings" $null $anna.Headers
Assert-Status "Abgesagten Termin buchen -> 409" $r 409

$r = Invoke-Api POST "/slots/00000000-0000-0000-0000-000000000000/bookings" $null $anna.Headers
Assert-Status "Unbekannten Termin buchen -> 404" $r 404

$r = Invoke-Api POST "/slots/$($slot1.id)/cancel" $null $script:companyHeaders
Assert-Status "Unternehmen sagt Termin mit Buchungen ab -> 200" $r 200
$r = Invoke-Api GET "/participants/me/bookings" $null $ben.Headers
$benStatus = (@($r.Data) | Where-Object { $_.slotId -eq $slot1.id } | Select-Object -First 1).status
Assert-True "bens Buchung wurde automatisch storniert" ($benStatus -eq "CANCELLED")
Assert-Mail "Absage-Mail an ben" $ben.Email "Abgesagt"
Assert-Mail "Absage-Mail an carla" $carla.Email "Abgesagt"

if ($SkipRaceTest) {
    Write-Host "  [SKIP] Ueberbuchungstest (-SkipRaceTest)" -ForegroundColor Yellow
} else {
    Write-Host "  ... Ueberbuchungstest: 5 Personen buchen gleichzeitig 1 Platz (ca. 10 Sekunden)" -ForegroundColor Gray
    $raceSlot = New-TestSlot (7 * 24 * 60) 1
    $racers = 1..5 | ForEach-Object { New-TestParticipant "racer$_" }
    $go = (Get-Date).AddSeconds(8)
    $jobs = foreach ($racer in $racers) {
        Start-Job -ArgumentList $api, $raceSlot.id, $racer.Headers.Authorization, $go -ScriptBlock {
            param($api, $slotId, $auth, $go)
            while ((Get-Date) -lt $go) { Start-Sleep -Milliseconds 5 }
            try {
                Invoke-WebRequest -Uri "$api/slots/$slotId/bookings" -Method Post -Headers @{ Authorization = $auth } -UseBasicParsing -ErrorAction Stop | Out-Null
                201
            } catch {
                if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { -1 }
            }
        }
    }
    $results = @($jobs | Wait-Job | Receive-Job)
    $jobs | Remove-Job
    $created = @($results | Where-Object { $_ -eq 201 }).Count
    $rejected = @($results | Where-Object { $_ -eq 409 }).Count
    Assert-True "Genau 1x gebucht, 4x abgelehnt (bekommen: $created x 201, $rejected x 409)" ($created -eq 1 -and $rejected -eq 4)
    $r = Invoke-Api GET "/companies/me/slots/$($raceSlot.id)/bookings" $null $script:companyHeaders
    $confirmed = @(@($r.Data) | Where-Object { $_.status -eq "CONFIRMED" }).Count
    Assert-True "In der Datenbank: genau 1 bestaetigte Buchung (bekommen: $confirmed)" ($confirmed -eq 1)
}

# ---------------------------------------------------------------------
#  Schritt 6: Bewertungen
# ---------------------------------------------------------------------

Write-Section "Schritt 6: Anwesenheit und Bewertungen"

$slot3 = New-TestSlot (7 * 24 * 60) 3
$annaBooking3 = (Invoke-Api POST "/slots/$($slot3.id)/bookings" $null $anna.Headers).Data
$benBooking3 = (Invoke-Api POST "/slots/$($slot3.id)/bookings" $null $ben.Headers).Data
$review = @{ rating = 5; comment = "Super Team, sehr lehrreich!"; interestedInJob = $true }

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/review" $review $anna.Headers
Assert-Status "Bewerten vor dem Probetag -> 409" $r 409

Move-SlotsToPast @($slot3.id) "2 hours"

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/attendance" @{} $script:companyHeaders
Assert-Status "Anwesenheit ohne 'attended' -> 400" $r 400

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/attendance" @{ attended = $true } $otherCompanyHeaders
Assert-Status "Fremdes Unternehmen markiert Anwesenheit -> 403" $r 403

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/attendance" @{ attended = $true } $script:companyHeaders
Assert-Status "anna war da -> 200" $r 200
Assert-True "Status ATTENDED" ($r.Data.status -eq "ATTENDED")

$r = Invoke-Api POST "/bookings/$($benBooking3.id)/attendance" @{ attended = $false } $script:companyHeaders
Assert-True "ben war nicht da -> NO_SHOW" ($r.Status -eq 200 -and $r.Data.status -eq "NO_SHOW")

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/review" @{ rating = 6 } $anna.Headers
Assert-Status "Note 6 -> 400" $r 400

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/review" $review $carla.Headers
Assert-Status "carla bewertet annas Buchung -> 403" $r 403

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/review" $review $anna.Headers
Assert-Status "anna bewertet -> 201" $r 201

$r = Invoke-Api POST "/bookings/$($annaBooking3.id)/review" $review $anna.Headers
Assert-Status "anna bewertet nochmal -> 409" $r 409

$r = Invoke-Api POST "/bookings/$($benBooking3.id)/review" $review $ben.Headers
Assert-Status "ben (nicht erschienen) bewertet -> 409" $r 409

$r = Invoke-Api GET "/companies/$companyId/reviews"
Assert-Status "Oeffentliche Bewertungen ohne Login -> 200" $r 200
Assert-True "1 Bewertung, Durchschnitt 5.0" ($r.Data.reviewCount -eq 1 -and $r.Data.averageRating -eq 5)
$publicReview = @($r.Data.reviews)[0]
Assert-True "Oeffentlich: keine E-Mail und kein 'interestedInJob'" ($null -eq $publicReview.PSObject.Properties['email'] -and $null -eq $publicReview.PSObject.Properties['interestedInJob'])

$r = Invoke-Api GET "/companies/me/reviews" $null $script:companyHeaders
$companyReview = @($r.Data)[0]
Assert-True "Unternehmen sieht E-Mail und Interesse an einer Stelle" ($companyReview.email -eq $anna.Email -and $companyReview.interestedInJob -eq $true)

$r = Invoke-Api GET "/companies/me/reviews"
Assert-Status "Firmen-Bewertungen ohne Token -> 401" $r 401

$r = Invoke-Api GET "/companies/00000000-0000-0000-0000-000000000000/reviews"
Assert-Status "Bewertungen eines unbekannten Unternehmens -> 404" $r 404

# ---------------------------------------------------------------------
#  Schritt 7b: Konten sperren und No-Show-Sperre
# ---------------------------------------------------------------------

Write-Section "Schritt 7b: Konten sperren"

$slot4 = New-TestSlot (7 * 24 * 60) 3

$r = Invoke-Api POST "/admin/users/$companyUserId/block" $null $adminHeaders
Assert-Status "Admin sperrt das Unternehmen -> 204" $r 204

$r = Invoke-Api GET "/slots"
Assert-True "Termine des gesperrten Unternehmens sind verschwunden" (-not (Test-ContainsSlot $r.Data $slot4.id))

$r = Invoke-Api POST "/auth/login" @{ email = $companyEmail; password = "geheim123" }
Assert-Status "Gesperrtes Unternehmen kann sich nicht einloggen -> 401" $r 401

$r = Invoke-Api POST "/admin/users/$companyUserId/unblock" $null $adminHeaders
Assert-Status "Admin entsperrt -> 204" $r 204

$r = Invoke-Api GET "/slots"
Assert-True "Termine sind wieder sichtbar" (Test-ContainsSlot $r.Data $slot4.id)

$r = Invoke-Api POST "/admin/users/$adminUserId/block" $null $adminHeaders
Assert-Status "Admin sperren -> 403" $r 403

$r = Invoke-Api POST "/admin/users/00000000-0000-0000-0000-000000000000/block" $null $adminHeaders
Assert-Status "Unbekannten Benutzer sperren -> 404" $r 404

Write-Section "Schritt 7c: No-Show-Sperre"

$felix = New-TestParticipant "felix"
$felixSlots = @(1..3 | ForEach-Object { New-TestSlot (7 * 24 * 60) 3 })
$felixBookings = @($felixSlots | ForEach-Object { (Invoke-Api POST "/slots/$($_.id)/bookings" $null $felix.Headers).Data })
Move-SlotsToPast ($felixSlots | ForEach-Object { $_.id }) "2 days"
$marked = @($felixBookings | ForEach-Object { Invoke-Api POST "/bookings/$($_.id)/attendance" @{ attended = $false } $script:companyHeaders } | Where-Object { $_.Data.status -eq "NO_SHOW" }).Count
Assert-True "felix 3x als NO_SHOW markiert" ($marked -eq 3)

$r = Invoke-Api POST "/slots/$($slot4.id)/bookings" $null $felix.Headers
Assert-Status "felix bucht nach 3 No-Shows -> 403" $r 403

Move-SlotsToPast ($felixSlots | ForEach-Object { $_.id }) "31 days"
$r = Invoke-Api POST "/slots/$($slot4.id)/bookings" $null $felix.Headers
Assert-Status "Nach 31 Tagen darf felix wieder buchen -> 201" $r 201

# ---------------------------------------------------------------------
#  Optional: Erinnerung am Vortag
# ---------------------------------------------------------------------

if ($WithReminder) {
    Write-Section "Schritt 5.4: Erinnerung am Vortag"
    $tomorrow8 = (Get-Date).Date.AddDays(1).AddHours(8)
    $slotTomorrow = New-TestSlot ($tomorrow8 - (Get-Date)).TotalMinutes 3
    $r = Invoke-Api POST "/slots/$($slotTomorrow.id)/bookings" $null $carla.Headers
    Assert-Status "carla bucht Termin morgen 08:00 -> 201" $r 201
    Write-Host "  ... warte bis zu 70 Sekunden auf den Erinnerungs-Job" -ForegroundColor Gray
    Assert-Mail "Erinnerungs-Mail an carla" $carla.Email "Erinnerung" 70
    Write-Host "  ... warte 65 Sekunden, ob eine zweite Erinnerung kommt" -ForegroundColor Gray
    Start-Sleep -Seconds 65
    Assert-True "Keine doppelte Erinnerung" (@(Find-Mail $carla.Email "Erinnerung").Count -eq 1)
}

# ---------------------------------------------------------------------
#  Ergebnis
# ---------------------------------------------------------------------

Write-Host ""
Write-Host "==============================================" -ForegroundColor White
if ($script:failed -eq 0) {
    Write-Host "  ALLE $($script:passed) TESTS BESTANDEN" -ForegroundColor Green
} else {
    Write-Host "  $($script:passed) bestanden, $($script:failed) FEHLGESCHLAGEN:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "   - $_" -ForegroundColor Red }
}
Write-Host "==============================================" -ForegroundColor White
if ($script:failed -gt 0) { exit 1 }

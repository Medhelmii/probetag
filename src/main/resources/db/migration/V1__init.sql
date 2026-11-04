CREATE TABLE users (
                       id            UUID PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       role          VARCHAR(20)  NOT NULL CHECK (role IN ('COMPANY','PARTICIPANT','ADMIN')),
                       enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE companies (
                           id          UUID PRIMARY KEY,
                           user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                           name        VARCHAR(200) NOT NULL,
                           description TEXT,
                           sector      VARCHAR(100) NOT NULL,
                           address     VARCHAR(255) NOT NULL,
                           city        VARCHAR(100) NOT NULL,
                           postal_code VARCHAR(10)  NOT NULL,
                           verified    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE participants (
                              id         UUID PRIMARY KEY,
                              user_id    UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                              first_name VARCHAR(100) NOT NULL,
                              last_name  VARCHAR(100) NOT NULL,
                              phone      VARCHAR(30),
                              motivation TEXT
);

CREATE TABLE slots (
                       id          UUID PRIMARY KEY,
                       company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
                       job_title   VARCHAR(150) NOT NULL,
                       description TEXT NOT NULL,
                       starts_at   TIMESTAMPTZ NOT NULL,
                       ends_at     TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
                       capacity    INT NOT NULL CHECK (capacity BETWEEN 1 AND 20),
                       dress_code  VARCHAR(255),
                       status      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
);
CREATE INDEX idx_slots_starts_at ON slots(starts_at);

CREATE TABLE bookings (
                          id             UUID PRIMARY KEY,
                          slot_id        UUID NOT NULL REFERENCES slots(id) ON DELETE CASCADE,
                          participant_id UUID NOT NULL REFERENCES participants(id) ON DELETE CASCADE,
                          status         VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
                          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                          version        INT NOT NULL DEFAULT 0,
                          UNIQUE (slot_id, participant_id)
);

CREATE TABLE reviews (
                         id                UUID PRIMARY KEY,
                         booking_id        UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
                         rating            INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                         comment           TEXT,
                         interested_in_job BOOLEAN NOT NULL DEFAULT FALSE
);
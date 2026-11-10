-- A transactional build blocks writers; deploy during the same planned write pause as the backfill.
SET LOCAL lock_timeout = '10s';
CREATE INDEX soknad_owner_status_idx
    ON soknad ((owner ->> 'idType'), (owner ->> 'id'), status)
    WHERE visningstype IN ('0', '1', '2', '3');

CREATE INDEX soknad_affected_person_status_idx
    ON soknad ((affecteduser ->> 'idType'), (affecteduser ->> 'id'), status)
    WHERE visningstype IN ('0', '1', '2', '3');

-- Keep validation and updates in the same Flyway transaction, excluding all writers.
SET LOCAL lock_timeout = '10s';
LOCK TABLE soknad IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    invalid_rows bigint;
BEGIN
    SELECT count(*) INTO invalid_rows
    FROM soknad
    WHERE visningstype IS NULL OR visningstype NOT IN ('0', '1', '2', '3', '4')
       OR (visningstype IN ('0', '1', '2', '3') AND brukerid IS NULL)
       OR (brukerid IS NOT NULL AND brukerid !~ '^[0-9]{11}$')
       OR (brukertype IS NOT NULL AND brukertype <> '0')
       -- Exact object equality also rejects wrong JSON structure, types and extra fields.
       OR (owner IS NOT NULL AND (
           visningstype = '4'
           OR (owner ->> 'id' ~ '^[0-9]{11}$') IS NOT TRUE
           OR owner IS DISTINCT FROM jsonb_build_object('id', owner ->> 'id', 'idType', 'FNR')))
       -- A digital affected party may legitimately differ from the owner.
       OR (affecteduser IS NOT NULL AND (
           (affecteduser ->> 'id' ~ '^[0-9]{11}$') IS NOT TRUE
           OR affecteduser IS DISTINCT FROM jsonb_build_object('id', affecteduser ->> 'id', 'idType', 'FNR')))
       OR (visningstype = '4' AND brukerid IS NOT NULL AND affecteduser IS NOT NULL
           AND affecteduser IS DISTINCT FROM jsonb_build_object('id', brukerid, 'idType', 'FNR'));

    IF invalid_rows > 0 THEN
        RAISE EXCEPTION 'Owner backfill rejected: invalid_rows=%', invalid_rows;
    END IF;

    UPDATE soknad
    SET owner = jsonb_build_object('id', brukerid, 'idType', 'FNR')
    WHERE visningstype IN ('0', '1', '2', '3') AND owner IS NULL;

    -- Preserve the historical archiving identity independently of digital access ownership.
    UPDATE soknad
    SET affecteduser = jsonb_build_object('id', brukerid, 'idType', 'FNR')
    WHERE visningstype IN ('0', '1', '2', '3', '4')
      AND affecteduser IS NULL AND brukerid IS NOT NULL;
END;
$$;

DROP TRIGGER IF EXISTS before_update_soknad_guard ON soknad;
DROP FUNCTION IF EXISTS prevent_illegal_status_change_for_soknad();

CREATE FUNCTION prevent_illegal_status_change_for_soknad()
	RETURNS TRIGGER
	LANGUAGE PLPGSQL
AS $$
BEGIN
IF OLD.status = 'Innsendt' AND NEW.status <> 'Innsendt' THEN
	RAISE EXCEPTION 'Cannot change status from Innsendt';
END IF;
IF OLD.status = 'KlarForInnsending' AND OLD.status <> NEW.status AND NEW.status <> 'Innsendt' THEN
	RAISE EXCEPTION 'Cannot only change status to Innsendt when current status is KlarForInnsending';
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER before_update_soknad_guard
	BEFORE UPDATE
	ON soknad
	FOR EACH ROW
	EXECUTE FUNCTION prevent_illegal_status_change_for_soknad();

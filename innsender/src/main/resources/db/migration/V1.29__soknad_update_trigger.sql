CREATE FUNCTION prevent_illegal_status_change_for_soknad()
	RETURNS TRIGGER
	LANGUAGE PLPGSQL
AS $$
BEGIN
IF OLD.status = 'Innsendt' AND NEW.status = 'Utfylt' THEN
	RAISE EXCEPTION 'Cannot change status from Innsendt to Utfylt';
END IF;
RETURN NEW;
END;
$$;

CREATE TRIGGER before_update_soknad_guard
	BEFORE UPDATE
	ON soknad
	FOR EACH ROW
	EXECUTE FUNCTION prevent_illegal_status_change_for_soknad();

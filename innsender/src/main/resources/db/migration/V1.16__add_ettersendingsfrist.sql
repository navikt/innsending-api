ALTER TABLE soknad ADD COLUMN forsteinnsendingsdato TIMESTAMP WITH TIME ZONE;
ALTER TABLE soknad ADD COLUMN ettersendingsfrist INT DEFAULT 14;

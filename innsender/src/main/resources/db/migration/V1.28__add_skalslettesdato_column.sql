ALTER TABLE soknad
ADD COLUMN skalslettesdato TIMESTAMP WITH TIME ZONE;

UPDATE soknad
SET skalslettesdato = opprettetdato + INTERVAL '28 days'
WHERE skalslettesdato IS NULL;

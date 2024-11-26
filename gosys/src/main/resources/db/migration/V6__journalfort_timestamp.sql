ALTER TABLE vedtak_fattet ADD COLUMN journalfort TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN vedtak_fattet.journalfort is 'Tidspunkt journalføring ble gjort. Om dette er før 1980 så vet vi ikke når journalføringen ble gjennomført';

UPDATE vedtak_fattet SET journalfort='1979-01-01 00:00:00';
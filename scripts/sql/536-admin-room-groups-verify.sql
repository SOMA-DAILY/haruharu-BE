DO $$
BEGIN
    IF (SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()
        AND ((table_name='admin_room_groups' AND column_name IN ('id','name'))
          OR (table_name='admin_room_organizations' AND column_name IN ('room_id','group_id','is_test')))) <> 5 THEN
        RAISE EXCEPTION 'Admin room organization columns missing';
    END IF;
    IF (SELECT count(*) FROM pg_constraint WHERE conrelid='admin_room_organizations'::regclass AND contype='f') <> 2 THEN
        RAISE EXCEPTION 'Admin room organization foreign keys missing';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='admin_room_groups'::regclass AND conname='uk_admin_room_group_name' AND contype='u') THEN
        RAISE EXCEPTION 'Admin room group name uniqueness missing';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='admin_room_organizations' AND column_name='is_test' AND is_nullable='YES' AND column_default IS NULL) THEN
        RAISE EXCEPTION 'Legacy room classification must remain unknown';
    END IF;
END $$;

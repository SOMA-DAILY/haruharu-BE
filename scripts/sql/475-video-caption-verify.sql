SELECT table_name, column_name, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name IN ('video_posts', 'video_upload_sessions') AND column_name = 'caption';

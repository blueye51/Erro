-- Plain PostgreSQL remains supported. A database owner may enable vector later.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector')
       AND NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        BEGIN
            CREATE EXTENSION vector WITH SCHEMA public;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'vector available but not enabled: use array cosine fallback until enabled by the database owner';
        END;
    END IF;
END $$;

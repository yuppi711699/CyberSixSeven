ALTER TABLE submissions
    ADD COLUMN accessory_key text;

ALTER TABLE submissions
    ADD CONSTRAINT submissions_accessory_key_private
    CHECK (
        accessory_key IS NULL
        OR (
            accessory_key NOT LIKE 'http%'
            AND accessory_key ~ '^accessories/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/reward\.stl$'
        )
    );

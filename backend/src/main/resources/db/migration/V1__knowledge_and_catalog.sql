-- Dedicated Flyway schema: never baseline, alter, or drop existing public tables.
CREATE TABLE knowledge_document (
    id uuid PRIMARY KEY,
    title varchar(300) NOT NULL,
    description varchar(2000) NOT NULL DEFAULT '',
    source_type varchar(40) NOT NULL,
    publisher varchar(200) NOT NULL,
    source_url varchar(2000) NOT NULL DEFAULT '',
    jurisdiction varchar(40) NOT NULL,
    language varchar(16) NOT NULL DEFAULT 'en',
    document_version varchar(100) NOT NULL DEFAULT '',
    edition varchar(100) NOT NULL DEFAULT '',
    amendment varchar(100) NOT NULL DEFAULT '',
    publication_date date,
    effective_date date,
    standard_number varchar(100) NOT NULL DEFAULT '',
    standard_family varchar(100) NOT NULL DEFAULT '',
    manufacturer varchar(200) NOT NULL DEFAULT '',
    product_family varchar(200) NOT NULL DEFAULT '',
    copyright_status varchar(30) NOT NULL,
    license_notes varchar(2000) NOT NULL DEFAULT '',
    enabled boolean NOT NULL DEFAULT true,
    content text NOT NULL,
    source_hash char(64) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','INDEXING','READY','FAILED')),
    error varchar(300),
    last_fetched_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_url, document_version, source_hash)
);
CREATE INDEX knowledge_document_scope ON knowledge_document (enabled, jurisdiction, source_type);
CREATE INDEX knowledge_document_standard ON knowledge_document (standard_number);
CREATE TABLE knowledge_chunk (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    section_title varchar(300) NOT NULL,
    section_path varchar(2000) NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    token_count integer NOT NULL,
    embedding double precision[],
    embedding_model varchar(300),
    metadata jsonb NOT NULL DEFAULT '{}',
    search_vector tsvector NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);
CREATE INDEX knowledge_chunk_search ON knowledge_chunk USING gin (search_vector);
CREATE INDEX knowledge_chunk_embedding_model ON knowledge_chunk (embedding_model) WHERE embedding IS NOT NULL;

CREATE TABLE product (
    id uuid PRIMARY KEY,
    manufacturer varchar(200) NOT NULL,
    series varchar(200) NOT NULL DEFAULT '',
    part_number varchar(200) NOT NULL,
    gtin varchar(30),
    product_type varchar(40) NOT NULL,
    name varchar(300) NOT NULL,
    description varchar(2000) NOT NULL DEFAULT '',
    rated_voltage_ac numeric CHECK (rated_voltage_ac > 0),
    rated_voltage_dc numeric CHECK (rated_voltage_dc > 0),
    rated_current numeric CHECK (rated_current > 0),
    frequency_min numeric,
    frequency_max numeric,
    phase_count integer,
    number_of_poles integer,
    trip_curve varchar(20),
    trip_unit varchar(100),
    residual_current numeric,
    rcd_type varchar(30),
    coil_voltage numeric,
    coil_voltage_type varchar(4) CHECK (coil_voltage_type IN ('AC','DC')),
    input_voltage_min numeric,
    input_voltage_max numeric,
    output_voltage numeric,
    output_current numeric,
    coordination_type varchar(100),
    conductor_min numeric,
    conductor_max numeric,
    conductor_material varchar(100),
    terminal_type varchar(100),
    ip_rating varchar(10),
    operating_temperature_min numeric,
    operating_temperature_max numeric,
    mounting_method varchar(100),
    ce boolean,
    rohs boolean,
    datasheet_url varchar(2000),
    manual_url varchar(2000),
    manufacturer_product_url varchar(2000),
    source_document_id uuid NOT NULL REFERENCES knowledge_document(id),
    additional_attributes jsonb NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (manufacturer, part_number)
);
CREATE INDEX product_selection ON product (product_type, manufacturer, rated_current);
-- Ratings belong to particular voltage, current type, utilization and standard conditions.
CREATE TABLE product_rating (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    voltage numeric NOT NULL CHECK (voltage > 0),
    current_type varchar(4) NOT NULL CHECK (current_type IN ('AC','DC')),
    utilization_category varchar(30),
    rated_current numeric CHECK (rated_current > 0),
    motor_power numeric CHECK (motor_power > 0),
    icu numeric CHECK (icu > 0),
    ics numeric CHECK (ics > 0),
    icn numeric CHECK (icn > 0),
    standard_number varchar(100),
    conditions varchar(2000) NOT NULL DEFAULT ''
);
CREATE INDEX product_rating_product ON product_rating (product_id, voltage, current_type);
CREATE TABLE product_standard (
    product_id uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    standard_number varchar(100) NOT NULL,
    edition varchar(100) NOT NULL DEFAULT '',
    certification varchar(300) NOT NULL DEFAULT '',
    PRIMARY KEY (product_id, standard_number, edition)
);
CREATE TABLE product_compatibility (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    related_product_id uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    compatibility_type varchar(60) NOT NULL,
    conditions varchar(2000) NOT NULL,
    source_document_id uuid NOT NULL REFERENCES knowledge_document(id),
    verified boolean NOT NULL DEFAULT false,
    notes varchar(2000) NOT NULL DEFAULT '',
    CHECK (product_id <> related_product_id),
    UNIQUE (product_id, related_product_id, compatibility_type)
);
CREATE INDEX product_compatibility_related ON product_compatibility(related_product_id);

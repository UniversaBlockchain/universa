ALTER TABLE name_storage DROP COLUMN url;
ALTER TABLE name_entry ADD COLUMN environment_id bigint references environments(id) on delete cascade;
DROP index ix_name_entry_name_storage_id;
ALTER TABLE name_entry DROP COLUMN name_storage_id;
CREATE index ix_name_entry_environment_id ON name_entry(environment_id);

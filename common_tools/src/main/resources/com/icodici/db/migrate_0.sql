-- noinspection SqlNoDataSourceInspectionForFile

create table vars(
    id identity primary key,
    name varchar(64),
    ivalue int,
    svalue text,
    data blob
);

create unique index id_var_names on vars(name);

-- important
insert into vars(name, ivalue) values('version', 1);


alter table menu_registered_permissions_record
    add permission_enabled tinyint default 1 not null after permission_id;

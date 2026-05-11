-- Seed Asset Type

INSERT INTO asset_types(name, code, description) VALUES
    ('Gold Coins',          'GOLD',             'Primary in-game currency for purchases'),
    ('Diamonds',            'DIAMOND',          'Premium currency for rare items'),
    ('Loyalty Points',      'LOYALTY',          'Earned through activity') ;


-- Seed System (Treasury) Wallets

INSERT INTO wallets (owner_id, owner_type, asset_type_id, balance) VALUES
    ('SYSTEM_TREASURY','SYSTEM', (SELECT id FROM asset_types WHERE code = 'GOLD'), 999999999),
    ('SYSTEM_TREASURY','SYSTEM', (SELECT id FROM asset_types WHERE code = 'DIAMOND'), 999999999),
    ('SYSTEM_TREASURY','SYSTEM', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 999999999);

-- Seed User Wallets

INSERT INTO wallets (owner_id, owner_type, asset_type_id, balance) VALUES
    ('user-alice-001', 'USER', (SELECT id FROM asset_types WHERE code = 'GOLD'), 500),
    ('user-alice-001', 'USER', (SELECT id FROM asset_types WHERE code = 'DIAMOND'), 30),
    ('user-bob-002', 'USER', (SELECT id FROM asset_types WHERE code = 'GOLD'), 300),
    ('user-bob-002', 'USER', (SELECT id FROM asset_types WHERE code = 'DIAMOND'), 50),
    ('user-bob-002', 'USER', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 200),
    ('user-charlie-003', 'USER', (SELECT id FROM asset_types WHERE code = 'GOLD'), 1000),
    ('user-charlie-003', 'USER', (SELECT id FROM asset_types WHERE code = 'DIAMOND'), 100),
    ('user-charlie-003', 'USER', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 500),
    ('user-david-004', 'USER', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 1000),
    ('SYSTEM_REWARD_POOL', 'SYSTEM', (SELECT id FROM asset_types WHERE code = 'GOLD'), 1000000),
    ('SYSTEM_REWARD_POOL', 'SYSTEM', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 500000);

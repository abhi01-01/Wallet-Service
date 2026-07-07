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

-- Seed System Reward Pool Wallets

INSERT INTO wallets (owner_id, owner_type, asset_type_id, balance) VALUES
    ('SYSTEM_REWARD_POOL', 'SYSTEM', (SELECT id FROM asset_types WHERE code = 'GOLD'), 100000000),
    ('SYSTEM_REWARD_POOL', 'SYSTEM', (SELECT id FROM asset_types WHERE code = 'DIAMOND'), 100000000),
    ('SYSTEM_REWARD_POOL', 'SYSTEM', (SELECT id FROM asset_types WHERE code = 'LOYALTY'), 100000000);

-- fix pricing for micro usd denoms
UPDATE asset_pricing
SET pricing = 0.000001
WHERE denom in ('uusd.trading', 'uusdc.figure.se', 'uusdt.figure.se');

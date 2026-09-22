-- How much of each piece is each colour, as "name:percent" entries alongside the
-- existing colours list. Kept as text like every other list column here.
--
-- It is separate from `colors` rather than replacing it because the two can drift:
-- the colours are editable by hand, and a share measured from a photo says nothing
-- about a colour someone typed in afterwards. When they no longer line up the
-- shares are dropped rather than guessed.
ALTER TABLE wardrobe_items ADD COLUMN color_shares VARCHAR(500);

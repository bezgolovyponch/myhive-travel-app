-- Optional stag name: used in the vote page's OG title ("Vote on {name}'s stag do").
ALTER TABLE vote_sessions ADD COLUMN groom_name VARCHAR(100);

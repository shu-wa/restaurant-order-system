INSERT INTO shop (name) VALUES ('サンプル中央店'), ('サンプル海岸店');
INSERT INTO allergen (name) VALUES ('卵'), ('乳'), ('小麦'), ('えび');
INSERT INTO menu (name, calories) VALUES
('きのこパスタ', 520), ('彩りサラダ', 120), ('チーズドリア', 640),
('季節のスープ', 180), ('えびグラタン', 580), ('小さなプリン', 210);
INSERT INTO menu_allergen VALUES (1,3), (3,2), (3,3), (4,2), (5,2), (5,3), (5,4), (6,1), (6,2);
INSERT INTO offering (shop_id, menu_id, price_yen, stock, limited) VALUES
(1,1,650,10,FALSE), (1,2,300,15,FALSE), (1,3,720,8,FALSE),
(1,4,250,5,TRUE), (1,5,800,0,FALSE), (1,6,220,12,FALSE),
(2,1,680,5,FALSE), (2,2,320,7,FALSE), (2,4,280,4,TRUE);

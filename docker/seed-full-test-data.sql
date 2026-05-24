SET NOCOUNT ON;

DECLARE @today date = CONVERT(date, GETDATE());
DECLARE @firstDate date = DATEADD(day, 1, @today);
DECLARE @passwordHash nvarchar(255) = N'$2a$10$7ysMsfH7.Aa5qurepWcavObcmPnBh39I24sPLm3WcV085H.76dpJq'; -- Admin@123

IF EXISTS (
    SELECT 1
    FROM sys.columns c
    JOIN sys.types ty ON c.user_type_id = ty.user_type_id
    WHERE c.object_id = OBJECT_ID(N'users')
      AND c.name = N'avatar'
      AND ty.name = N'text'
)
BEGIN
    ALTER TABLE users ALTER COLUMN avatar NVARCHAR(MAX) NULL;
END

IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Hành động') INSERT INTO genres(name) VALUES (N'Hành động');
IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Tình cảm') INSERT INTO genres(name) VALUES (N'Tình cảm');
IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Kinh dị') INSERT INTO genres(name) VALUES (N'Kinh dị');
IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Hoạt hình') INSERT INTO genres(name) VALUES (N'Hoạt hình');
IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Hài') INSERT INTO genres(name) VALUES (N'Hài');
IF NOT EXISTS (SELECT 1 FROM genres WHERE name = N'Viễn tưởng') INSERT INTO genres(name) VALUES (N'Viễn tưởng');

IF NOT EXISTS (SELECT 1 FROM membership_ranks WHERE rank_name = N'Hạng Đồng')
    INSERT INTO membership_ranks(rank_name, min_spending, description, discount_percent, bonus_point, status)
    VALUES (N'Hạng Đồng', 0, N'Hạng mặc định cho khách mới.', 0, 1, 1);
ELSE
    UPDATE membership_ranks SET min_spending = 0, discount_percent = 0, bonus_point = 1, status = 1 WHERE rank_name = N'Hạng Đồng';

IF NOT EXISTS (SELECT 1 FROM membership_ranks WHERE rank_name = N'Hạng Bạc')
    INSERT INTO membership_ranks(rank_name, min_spending, description, discount_percent, bonus_point, status)
    VALUES (N'Hạng Bạc', 1000000, N'Khách quay lại thường xuyên.', 3, 5, 1);
ELSE
    UPDATE membership_ranks SET min_spending = 1000000, discount_percent = 3, bonus_point = 5, status = 1 WHERE rank_name = N'Hạng Bạc';

IF NOT EXISTS (SELECT 1 FROM membership_ranks WHERE rank_name = N'Hạng Vàng')
    INSERT INTO membership_ranks(rank_name, min_spending, description, discount_percent, bonus_point, status)
    VALUES (N'Hạng Vàng', 3000000, N'Khách thân thiết.', 5, 10, 1);
ELSE
    UPDATE membership_ranks SET min_spending = 3000000, discount_percent = 5, bonus_point = 10, status = 1 WHERE rank_name = N'Hạng Vàng';

IF NOT EXISTS (SELECT 1 FROM membership_ranks WHERE rank_name = N'Hạng Kim Cương')
    INSERT INTO membership_ranks(rank_name, min_spending, description, discount_percent, bonus_point, status)
    VALUES (N'Hạng Kim Cương', 7000000, N'Khách VIP.', 8, 20, 1);
ELSE
    UPDATE membership_ranks SET min_spending = 7000000, discount_percent = 8, bonus_point = 20, status = 1 WHERE rank_name = N'Hạng Kim Cương';

IF NOT EXISTS (SELECT 1 FROM cinemas WHERE name = N'Galaxy Nguyễn Du')
    INSERT INTO cinemas(name, address, status) VALUES (N'Galaxy Nguyễn Du', N'116 Nguyễn Du, Quận 1, TP.HCM', 1);
IF NOT EXISTS (SELECT 1 FROM cinemas WHERE name = N'Beta Thủ Đức')
    INSERT INTO cinemas(name, address, status) VALUES (N'Beta Thủ Đức', N'216 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', 1);
IF NOT EXISTS (SELECT 1 FROM cinemas WHERE name = N'CGV Vincom Đồng Khởi')
    INSERT INTO cinemas(name, address, status) VALUES (N'CGV Vincom Đồng Khởi', N'72 Lê Thánh Tôn, Quận 1, TP.HCM', 1);

IF NOT EXISTS (SELECT 1 FROM seatype WHERE name = N'Thường')
    INSERT INTO seatype(name, surcharge, color, couple_seat) VALUES (N'Thường', 0, '#0D6EFD', 0);
IF NOT EXISTS (SELECT 1 FROM seatype WHERE name = N'VIP')
    INSERT INTO seatype(name, surcharge, color, couple_seat) VALUES (N'VIP', 30000, '#FFC107', 0);
IF NOT EXISTS (SELECT 1 FROM seatype WHERE name = N'Đôi')
    INSERT INTO seatype(name, surcharge, color, couple_seat) VALUES (N'Đôi', 20000, '#DC3545', 1);

DECLARE @cinemaId int;
DECLARE cinema_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT cinema_id FROM cinemas WHERE ISNULL(status, 1) = 1;
OPEN cinema_cursor;
FETCH NEXT FROM cinema_cursor INTO @cinemaId;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (SELECT 1 FROM rooms WHERE cinema_id = @cinemaId AND name = N'Phòng 1')
        INSERT INTO rooms(name, status, cinema_id) VALUES (N'Phòng 1', 1, @cinemaId);
    IF NOT EXISTS (SELECT 1 FROM rooms WHERE cinema_id = @cinemaId AND name = N'Phòng 2')
        INSERT INTO rooms(name, status, cinema_id) VALUES (N'Phòng 2', 1, @cinemaId);
    FETCH NEXT FROM cinema_cursor INTO @cinemaId;
END
CLOSE cinema_cursor;
DEALLOCATE cinema_cursor;

DECLARE @regularSeatTypeId int = (SELECT TOP 1 seat_type_id FROM seatype WHERE name = N'Thường');
DECLARE @vipSeatTypeId int = (SELECT TOP 1 seat_type_id FROM seatype WHERE name = N'VIP');
DECLARE @coupleSeatTypeId int = (SELECT TOP 1 seat_type_id FROM seatype WHERE name = N'Đôi');
DECLARE @roomId int;
DECLARE room_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT room_id FROM rooms WHERE ISNULL(status, 1) = 1;
OPEN room_cursor;
FETCH NEXT FROM room_cursor INTO @roomId;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seats WHERE room_id = @roomId)
    BEGIN
        ;WITH Rows(row_name, row_order) AS (
            SELECT 'A', 1 UNION ALL SELECT 'B', 2 UNION ALL SELECT 'C', 3
            UNION ALL SELECT 'D', 4 UNION ALL SELECT 'E', 5 UNION ALL SELECT 'F', 6
        ),
        Nums(n) AS (
            SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
        )
        INSERT INTO seats(number, [row], x, y, status, room_id, seat_type_id)
        SELECT
            CONVERT(varchar(10), n),
            row_name,
            n,
            row_order,
            '1',
            @roomId,
            CASE
                WHEN row_name = 'F' THEN @coupleSeatTypeId
                WHEN row_name IN ('D', 'E') THEN @vipSeatTypeId
                ELSE @regularSeatTypeId
            END
        FROM Rows CROSS JOIN Nums;
    END
    FETCH NEXT FROM room_cursor INTO @roomId;
END
CLOSE room_cursor;
DEALLOCATE room_cursor;

IF NOT EXISTS (SELECT 1 FROM categories_products WHERE name = N'Bắp nước') INSERT INTO categories_products(name) VALUES (N'Bắp nước');
IF NOT EXISTS (SELECT 1 FROM categories_products WHERE name = N'Đồ uống') INSERT INTO categories_products(name) VALUES (N'Đồ uống');
IF NOT EXISTS (SELECT 1 FROM categories_products WHERE name = N'Snack') INSERT INTO categories_products(name) VALUES (N'Snack');

DECLARE @catCombo int = (SELECT TOP 1 categories_products_id FROM categories_products WHERE name = N'Bắp nước');
DECLARE @catDrink int = (SELECT TOP 1 categories_products_id FROM categories_products WHERE name = N'Đồ uống');
DECLARE @catSnack int = (SELECT TOP 1 categories_products_id FROM categories_products WHERE name = N'Snack');

IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Combo Bắp Nước Lớn')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Combo Bắp Nước Lớn', N'Bắp caramel lớn và 1 nước size L.', 79000, N'https://picsum.photos/seed/popcorn-combo/500/500', 1, @catCombo);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Combo Couple')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Combo Couple', N'2 nước size M và 1 bắp phô mai.', 119000, N'https://picsum.photos/seed/couple-combo/500/500', 1, @catCombo);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Combo Family')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Combo Family', N'2 bắp lớn và 4 nước size M.', 199000, N'https://picsum.photos/seed/family-combo/500/500', 1, @catCombo);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Bắp Caramel')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Bắp Caramel', N'Bắp rang caramel size M.', 55000, N'https://picsum.photos/seed/caramel-popcorn/500/500', 1, @catSnack);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Hotdog')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Hotdog', N'Bánh hotdog nóng.', 45000, N'https://picsum.photos/seed/hotdog/500/500', 1, @catSnack);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Pepsi')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Pepsi', N'Nước ngọt Pepsi size M.', 30000, N'https://picsum.photos/seed/pepsi/500/500', 1, @catDrink);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'7Up')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'7Up', N'Nước ngọt 7Up size M.', 30000, N'https://picsum.photos/seed/7up/500/500', 1, @catDrink);
IF NOT EXISTS (SELECT 1 FROM products WHERE name = N'Nước Suối')
    INSERT INTO products(name, description, price, image, status, categories_products_id)
    VALUES (N'Nước Suối', N'Nước suối đóng chai.', 20000, N'https://picsum.photos/seed/water/500/500', 1, @catDrink);

INSERT INTO cinema_products(cinema_id, product_id, is_active)
SELECT c.cinema_id, p.product_id, 1
FROM cinemas c
CROSS JOIN products p
WHERE ISNULL(c.status, 1) = 1
  AND ISNULL(p.status, 1) = 1
  AND NOT EXISTS (
      SELECT 1 FROM cinema_products cp
      WHERE cp.cinema_id = c.cinema_id AND cp.product_id = p.product_id
  );

IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Đêm Cuối Ở Sài Gòn')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Đêm Cuối Ở Sài Gòn', N'Phim hành động trinh thám nhịp nhanh.', N'Một điều tra viên lần theo vụ án cuối cùng trong đêm mưa Sài Gòn.', 125, N'Đạo diễn A', N'Việt Nam', DATEADD(day, -20, @today), 16, N'https://picsum.photos/seed/dem-cuoi-sai-gon/500/750', N'https://picsum.photos/seed/dem-cuoi-banner/1400/500', 1, 90000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Hành động'));
IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Nắng Sau Cơn Mưa')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Nắng Sau Cơn Mưa', N'Câu chuyện tình cảm nhẹ nhàng.', N'Hai người trẻ gặp lại nhau sau nhiều năm xa cách.', 110, N'Đạo diễn B', N'Việt Nam', DATEADD(day, -12, @today), 13, N'https://picsum.photos/seed/nang-sau-mua/500/750', N'https://picsum.photos/seed/nang-banner/1400/500', 1, 85000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Tình cảm'));
IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Căn Phòng Số 13')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Căn Phòng Số 13', N'Kinh dị tâm lý trong khách sạn cũ.', N'Mỗi đêm căn phòng lại hé lộ một bí mật khác.', 102, N'Đạo diễn C', N'Hàn Quốc', DATEADD(day, -8, @today), 18, N'https://picsum.photos/seed/room-13/500/750', N'https://picsum.photos/seed/room-13-banner/1400/500', 1, 88000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Kinh dị'));
IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Robot Và Thành Phố Mây')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Robot Và Thành Phố Mây', N'Hoạt hình phiêu lưu cho gia đình.', N'Một robot nhỏ tìm đường trở về thành phố trên mây.', 98, N'Đạo diễn D', N'Nhật Bản', DATEADD(day, -5, @today), 6, N'https://picsum.photos/seed/robot-cloud/500/750', N'https://picsum.photos/seed/robot-cloud-banner/1400/500', 1, 80000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Hoạt hình'));
IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Phi Vụ Không Gian')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Phi Vụ Không Gian', N'Viễn tưởng hành động ngoài vũ trụ.', N'Một phi hành đoàn thực hiện nhiệm vụ cuối cùng để cứu trạm không gian.', 132, N'Đạo diễn E', N'Mỹ', DATEADD(day, -2, @today), 13, N'https://picsum.photos/seed/space-mission/500/750', N'https://picsum.photos/seed/space-mission-banner/1400/500', 1, 95000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Viễn tưởng'));
IF NOT EXISTS (SELECT 1 FROM movie WHERE title = N'Gia Đình Bá Đạo')
    INSERT INTO movie(title, description, content, duration, author, nation, release_date, age_limit, poster, banner, status, base_price, genre_id)
    VALUES (N'Gia Đình Bá Đạo', N'Hài gia đình vui nhộn.', N'Một chuyến du lịch gia đình biến thành chuỗi tình huống dở khóc dở cười.', 105, N'Đạo diễn F', N'Việt Nam', @today, 6, N'https://picsum.photos/seed/funny-family/500/750', N'https://picsum.photos/seed/funny-family-banner/1400/500', 1, 82000, (SELECT TOP 1 genre_id FROM genres WHERE name = N'Hài'));

IF NOT EXISTS (SELECT 1 FROM vouchers WHERE code = N'TEST10')
    INSERT INTO vouchers(code, discount_type, value, min_order_value, max_discount_amount, start_date, end_date, point_voucher, status)
    VALUES (N'TEST10', N'PERCENT', 10, 100000, 30000, DATEADD(day, -1, @today), DATEADD(day, 60, @today), 100, 1);
IF NOT EXISTS (SELECT 1 FROM vouchers WHERE code = N'TEST50K')
    INSERT INTO vouchers(code, discount_type, value, min_order_value, max_discount_amount, start_date, end_date, point_voucher, status)
    VALUES (N'TEST50K', N'AMOUNT', 50000, 200000, 50000, DATEADD(day, -1, @today), DATEADD(day, 60, @today), 250, 1);
IF NOT EXISTS (SELECT 1 FROM vouchers WHERE code = N'VIP15')
    INSERT INTO vouchers(code, discount_type, value, min_order_value, max_discount_amount, start_date, end_date, point_voucher, status)
    VALUES (N'VIP15', N'PERCENT', 15, 250000, 60000, DATEADD(day, -1, @today), DATEADD(day, 60, @today), 300, 1);
IF NOT EXISTS (SELECT 1 FROM vouchers WHERE code = N'COMBO20')
    INSERT INTO vouchers(code, discount_type, value, min_order_value, max_discount_amount, start_date, end_date, point_voucher, status)
    VALUES (N'COMBO20', N'PERCENT', 20, 150000, 40000, DATEADD(day, -1, @today), DATEADD(day, 60, @today), 180, 1);

DECLARE @rankDong int = (SELECT TOP 1 rank_id FROM membership_ranks WHERE rank_name = N'Hạng Đồng');
DECLARE @rankBac int = (SELECT TOP 1 rank_id FROM membership_ranks WHERE rank_name = N'Hạng Bạc');
DECLARE @rankVang int = (SELECT TOP 1 rank_id FROM membership_ranks WHERE rank_name = N'Hạng Vàng');

IF NOT EXISTS (SELECT 1 FROM users WHERE username = N'testuser01')
    INSERT INTO users(username, password, fullname, status, birthday, avatar, email, phone, points, rank_id, total_spending)
    VALUES (N'testuser01', @passwordHash, N'Khách Test 01', 1, '2000-01-10', N'https://i.pravatar.cc/150?u=testuser01', N'testuser01@gmail.com', N'0909000001', 150, @rankDong, 350000);
ELSE
    UPDATE users SET password = @passwordHash, status = 1, rank_id = COALESCE(rank_id, @rankDong) WHERE username = N'testuser01';

IF NOT EXISTS (SELECT 1 FROM users WHERE username = N'testuser02')
    INSERT INTO users(username, password, fullname, status, birthday, avatar, email, phone, points, rank_id, total_spending)
    VALUES (N'testuser02', @passwordHash, N'Khách Test 02', 1, '1999-11-05', N'https://i.pravatar.cc/150?u=testuser02', N'testuser02@gmail.com', N'0909000002', 620, @rankBac, 1500000);
ELSE
    UPDATE users SET password = @passwordHash, status = 1, rank_id = COALESCE(rank_id, @rankBac) WHERE username = N'testuser02';

IF NOT EXISTS (SELECT 1 FROM users WHERE username = N'testuser03')
    INSERT INTO users(username, password, fullname, status, birthday, avatar, email, phone, points, rank_id, total_spending)
    VALUES (N'testuser03', @passwordHash, N'Khách Test 03', 1, '1997-07-20', N'https://i.pravatar.cc/150?u=testuser03', N'testuser03@gmail.com', N'0909000003', 1280, @rankVang, 3600000);
ELSE
    UPDATE users SET password = @passwordHash, status = 1, rank_id = COALESCE(rank_id, @rankVang) WHERE username = N'testuser03';

DECLARE @cinemaA int = (SELECT TOP 1 cinema_id FROM cinemas WHERE name = N'Galaxy Nguyễn Du');
DECLARE @cinemaB int = (SELECT TOP 1 cinema_id FROM cinemas WHERE name = N'Beta Thủ Đức');
DECLARE @cinemaC int = (SELECT TOP 1 cinema_id FROM cinemas WHERE name = N'CGV Vincom Đồng Khởi');

IF NOT EXISTS (SELECT 1 FROM staff WHERE username = N'testadmin01')
    INSERT INTO staff(email, username, password, fullname, status, phone, birthday, role, avatar, cinema_id)
    VALUES (N'testadmin01@cinema.local', N'testadmin01', @passwordHash, N'Quản Lý Test 01', 1, N'0911000001', '1994-04-12', N'ADMIN', N'https://i.pravatar.cc/150?u=testadmin01', @cinemaA);
ELSE
    UPDATE staff SET password = @passwordHash, status = 1, role = N'ADMIN', cinema_id = @cinemaA WHERE username = N'testadmin01';

IF NOT EXISTS (SELECT 1 FROM staff WHERE username = N'teststaff01')
    INSERT INTO staff(email, username, password, fullname, status, phone, birthday, role, avatar, cinema_id)
    VALUES (N'teststaff01@cinema.local', N'teststaff01', @passwordHash, N'Nhân Viên Bán Vé 01', 1, N'0911000002', '1998-03-15', N'STAFF', N'https://i.pravatar.cc/150?u=teststaff01', @cinemaA);
ELSE
    UPDATE staff SET password = @passwordHash, status = 1, role = N'STAFF', cinema_id = @cinemaA WHERE username = N'teststaff01';

IF NOT EXISTS (SELECT 1 FROM staff WHERE username = N'teststaff02')
    INSERT INTO staff(email, username, password, fullname, status, phone, birthday, role, avatar, cinema_id)
    VALUES (N'teststaff02@cinema.local', N'teststaff02', @passwordHash, N'Nhân Viên Soát Vé 02', 1, N'0911000003', '1998-06-25', N'STAFF', N'https://i.pravatar.cc/150?u=teststaff02', @cinemaB);
ELSE
    UPDATE staff SET password = @passwordHash, status = 1, role = N'STAFF', cinema_id = @cinemaB WHERE username = N'teststaff02';

INSERT INTO user_vouchers(user_id, voucher_id, status)
SELECT u.user_id, v.vouchers_id, 1
FROM users u
CROSS JOIN vouchers v
WHERE u.username IN (N'testuser01', N'testuser02', N'testuser03')
  AND v.code IN (N'TEST10', N'TEST50K', N'VIP15', N'COMBO20')
  AND NOT EXISTS (
      SELECT 1 FROM user_vouchers uv
      WHERE uv.user_id = u.user_id AND uv.voucher_id = v.vouchers_id AND uv.status = 1
  );

IF NOT EXISTS (SELECT 1 FROM promotions WHERE promotion_name = N'TEST Giảm 12% toàn hệ thống')
    INSERT INTO promotions(promotion_name, discount_percent, start_date, end_date, status, movie_id, cinema_id)
    VALUES (N'TEST Giảm 12% toàn hệ thống', 12, DATEADD(day, -1, @today), DATEADD(day, 30, @today), 1, NULL, NULL);
IF NOT EXISTS (SELECT 1 FROM promotions WHERE promotion_name = N'TEST Robot giảm 20%')
    INSERT INTO promotions(promotion_name, discount_percent, start_date, end_date, status, movie_id, cinema_id)
    VALUES (N'TEST Robot giảm 20%', 20, DATEADD(day, -1, @today), DATEADD(day, 20, @today), 1, (SELECT TOP 1 movie_id FROM movie WHERE title = N'Robot Và Thành Phố Mây'), NULL);
IF NOT EXISTS (SELECT 1 FROM promotions WHERE promotion_name = N'TEST Galaxy tối giảm 15%')
    INSERT INTO promotions(promotion_name, discount_percent, start_date, end_date, status, movie_id, cinema_id)
    VALUES (N'TEST Galaxy tối giảm 15%', 15, DATEADD(day, -1, @today), DATEADD(day, 25, @today), 1, NULL, @cinemaA);

IF NOT EXISTS (SELECT 1 FROM news WHERE title = N'TEST Lịch chiếu 10 ngày tới')
    INSERT INTO news(title, content, image, status, created_at)
    VALUES (N'TEST Lịch chiếu 10 ngày tới', N'<p>Hệ thống đã có dữ liệu suất chiếu, ghế, bắp nước và voucher để kiểm thử end to end.</p>', N'https://picsum.photos/seed/news-10-days/1200/600', 1, SYSDATETIME());
IF NOT EXISTS (SELECT 1 FROM news WHERE title = N'TEST Combo cuối tuần')
    INSERT INTO news(title, content, image, status, created_at)
    VALUES (N'TEST Combo cuối tuần', N'<p>Combo bắp nước dành cho khách đặt vé online và tại quầy.</p>', N'https://picsum.photos/seed/news-combo/1200/600', 1, SYSDATETIME());
IF NOT EXISTS (SELECT 1 FROM news WHERE title = N'TEST Thành viên nhận ưu đãi')
    INSERT INTO news(title, content, image, status, created_at)
    VALUES (N'TEST Thành viên nhận ưu đãi', N'<p>Khách có thể đổi voucher bằng điểm và theo dõi lịch sử điểm trong hồ sơ.</p>', N'https://picsum.photos/seed/news-member/1200/600', 1, SYSDATETIME());

DECLARE @movieCount int = (SELECT COUNT(*) FROM movie WHERE ISNULL(status, 1) = 1);
IF @movieCount > 0
BEGIN
    ;WITH Days(n) AS (
        SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
    ),
    Slots(slot_no, slot_minutes, surcharge) AS (
        SELECT 1, 570, 0.0 UNION ALL SELECT 2, 780, 0.0
        UNION ALL SELECT 3, 990, 10000.0 UNION ALL SELECT 4, 1200, 15000.0
    ),
    ActiveRooms AS (
        SELECT room_id, ROW_NUMBER() OVER (ORDER BY room_id) AS room_no
        FROM rooms WHERE ISNULL(status, 1) = 1
    ),
    ActiveMovies AS (
        SELECT movie_id, ROW_NUMBER() OVER (ORDER BY movie_id) AS movie_no
        FROM movie WHERE ISNULL(status, 1) = 1
    ),
    Planned AS (
        SELECT
            r.room_id,
            DATEADD(minute, s.slot_minutes, CAST(DATEADD(day, d.n, @firstDate) AS datetime2)) AS start_time,
            s.surcharge,
            ((r.room_no + d.n + s.slot_no - 2) % @movieCount) + 1 AS movie_no
        FROM ActiveRooms r
        CROSS JOIN Days d
        CROSS JOIN Slots s
    )
    INSERT INTO showtimes(movie_id, room_id, start_time, surcharge)
    SELECT m.movie_id, p.room_id, p.start_time, p.surcharge
    FROM Planned p
    JOIN ActiveMovies m ON m.movie_no = p.movie_no
    WHERE NOT EXISTS (
        SELECT 1 FROM showtimes st
        WHERE st.room_id = p.room_id AND st.start_time = p.start_time
    );
END

DECLARE @staff01 int = (SELECT TOP 1 staff_id FROM staff WHERE username = N'teststaff01');
DECLARE @staff02 int = (SELECT TOP 1 staff_id FROM staff WHERE username = N'teststaff02');
DECLARE @admin01 int = (SELECT TOP 1 staff_id FROM staff WHERE username = N'testadmin01');

;WITH Days(n) AS (
    SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
),
ShiftRows(staff_id, cinema_id, role_name, start_hour, end_hour) AS (
    SELECT @staff01, @cinemaA, N'Bán vé', 8, 16
    UNION ALL SELECT @staff02, @cinemaB, N'Soát vé', 13, 21
    UNION ALL SELECT @admin01, @cinemaA, N'Quản lý', 9, 18
)
INSERT INTO staff_shifts(staff_id, cinema_id, [date], start_time, end_time, role)
SELECT
    sr.staff_id,
    sr.cinema_id,
    DATEADD(day, d.n, @today),
    DATEADD(hour, sr.start_hour, CAST(DATEADD(day, d.n, @today) AS datetime2)),
    DATEADD(hour, sr.end_hour, CAST(DATEADD(day, d.n, @today) AS datetime2)),
    sr.role_name
FROM ShiftRows sr
CROSS JOIN Days d
WHERE sr.staff_id IS NOT NULL
  AND sr.cinema_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM staff_shifts ss
      WHERE ss.staff_id = sr.staff_id
        AND ss.[date] = DATEADD(day, d.n, @today)
        AND ss.start_time = DATEADD(hour, sr.start_hour, CAST(DATEADD(day, d.n, @today) AS datetime2))
  );

INSERT INTO favorites(user_id, movie_id)
SELECT u.user_id, m.movie_id
FROM users u
CROSS JOIN movie m
WHERE u.username IN (N'testuser01', N'testuser02', N'testuser03')
  AND m.title IN (N'Đêm Cuối Ở Sài Gòn', N'Robot Và Thành Phố Mây', N'Phi Vụ Không Gian')
  AND NOT EXISTS (
      SELECT 1 FROM favorites f
      WHERE f.user_id = u.user_id AND f.movie_id = m.movie_id
  );

DECLARE @testShowtime1 int = (SELECT TOP 1 showtime_id FROM showtimes WHERE start_time >= @firstDate ORDER BY start_time, showtime_id);
DECLARE @testShowtime2 int = (SELECT TOP 1 showtime_id FROM showtimes WHERE start_time >= @firstDate AND showtime_id <> @testShowtime1 ORDER BY start_time, showtime_id);
DECLARE @testRoom1 int = (SELECT room_id FROM showtimes WHERE showtime_id = @testShowtime1);
DECLARE @testRoom2 int = (SELECT room_id FROM showtimes WHERE showtime_id = @testShowtime2);
DECLARE @testSeat1 int = (SELECT TOP 1 seat_id FROM seats WHERE room_id = @testRoom1 ORDER BY y, x);
DECLARE @testSeat2 int = (SELECT TOP 1 seat_id FROM seats WHERE room_id = @testRoom1 AND seat_id <> @testSeat1 ORDER BY y, x);
DECLARE @testSeat3 int = (SELECT TOP 1 seat_id FROM seats WHERE room_id = @testRoom2 ORDER BY y, x);
DECLARE @testUser1 int = (SELECT TOP 1 user_id FROM users WHERE username = N'testuser01');
DECLARE @testUser2 int = (SELECT TOP 1 user_id FROM users WHERE username = N'testuser02');
DECLARE @productCombo int = (SELECT TOP 1 product_id FROM products WHERE name = N'Combo Bắp Nước Lớn');

IF @testUser1 IS NOT NULL AND @testShowtime1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders_online WHERE order_code = N'TEST-PAID-001')
BEGIN
    INSERT INTO orders_online(order_code, created_at, original_amount, discount_amount, final_amount, status, user_id, payment_method)
    VALUES (N'TEST-PAID-001', DATEADD(day, -2, SYSDATETIME()), 220000, 20000, 200000, 1, @testUser1, N'PAYOS');
    DECLARE @order1 int = CONVERT(int, SCOPE_IDENTITY());
    INSERT INTO tickets(status, price, original_price, promotion_discount, showtime_id, seat_id, order_online_id)
    VALUES (1, 90000, 100000, 10000, @testShowtime1, @testSeat1, @order1),
           (1, 90000, 100000, 10000, @testShowtime1, @testSeat2, @order1);
    IF @productCombo IS NOT NULL
        INSERT INTO oder_details_food(status, quantity, price, order_online_id, product_id)
        VALUES (1, 1, 79000, @order1, @productCombo);
    INSERT INTO points_histories([date], description, points, user_id)
    VALUES (DATEADD(day, -2, @today), N'Tích điểm từ đơn TEST-PAID-001', 200, @testUser1);
    UPDATE users SET points = ISNULL(points, 0) + 200, total_spending = ISNULL(total_spending, 0) + 200000 WHERE user_id = @testUser1;
END

IF @testUser2 IS NOT NULL AND @testShowtime2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM orders_online WHERE order_code = N'TEST-PAID-002')
BEGIN
    INSERT INTO orders_online(order_code, created_at, original_amount, discount_amount, final_amount, status, user_id, payment_method)
    VALUES (N'TEST-PAID-002', DATEADD(day, -1, SYSDATETIME()), 125000, 0, 125000, 1, @testUser2, N'PAYOS');
    DECLARE @order2 int = CONVERT(int, SCOPE_IDENTITY());
    INSERT INTO tickets(status, price, original_price, promotion_discount, showtime_id, seat_id, order_online_id)
    VALUES (1, 125000, 125000, 0, @testShowtime2, @testSeat3, @order2);
    INSERT INTO points_histories([date], description, points, user_id)
    VALUES (DATEADD(day, -1, @today), N'Tích điểm từ đơn TEST-PAID-002', 125, @testUser2);
    UPDATE users SET points = ISNULL(points, 0) + 125, total_spending = ISNULL(total_spending, 0) + 125000 WHERE user_id = @testUser2;
END

DECLARE @firstTicket int = (
    SELECT TOP 1 t.ticket_id
    FROM tickets t
    JOIN orders_online o ON o.order_online_id = t.order_online_id
    WHERE o.order_code = N'TEST-PAID-001'
    ORDER BY t.ticket_id
);
IF @firstTicket IS NOT NULL AND NOT EXISTS (SELECT 1 FROM reviews WHERE ticket_id = @firstTicket)
    INSERT INTO reviews(rating, comment, ticket_id)
    VALUES (5, N'Dữ liệu test: phim hay, đặt vé và cộng điểm hoạt động.', @firstTicket);

SELECT 'seed_full_test_data_done' AS result;
SELECT COUNT(*) AS genres FROM genres;
SELECT COUNT(*) AS movies FROM movie;
SELECT COUNT(*) AS cinemas FROM cinemas;
SELECT COUNT(*) AS rooms FROM rooms;
SELECT COUNT(*) AS seats FROM seats;
SELECT COUNT(*) AS products FROM products;
SELECT COUNT(*) AS cinema_products FROM cinema_products;
SELECT COUNT(*) AS showtimes FROM showtimes;
SELECT COUNT(*) AS users FROM users;
SELECT COUNT(*) AS staff FROM staff;
SELECT COUNT(*) AS vouchers FROM vouchers;
SELECT COUNT(*) AS shifts FROM staff_shifts;
SELECT COUNT(*) AS orders_online FROM orders_online;

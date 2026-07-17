-- Fix cho các vấn đề phát hiện khi kiểm tra database JAVA6 (2026-07-17)
-- 1. Dữ liệu trùng username/email khiến 2 unique constraint khai báo trong entity
--    (User.java) chưa từng được tạo ra ở DB do ddl-auto=update âm thầm bỏ qua khi gặp conflict.
-- 2. Toàn bộ 30 cột FK trong DB không có index hỗ trợ (SQL Server không tự tạo).
-- 3. Không có ràng buộc DB-level chống đặt trùng ghế (chỉ dựa vào code).
-- Script này idempotent - chạy lại nhiều lần không lỗi.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- ========== 1. Dedupe user_id=10 (bản sao rỗng, không có order/ticket/points nào) ==========
IF EXISTS (SELECT 1 FROM users WHERE user_id = 10 AND username = 'cong1234')
BEGIN
    UPDATE users
    SET username = username + '_dup10',
        email = 'dup10_' + email
    WHERE user_id = 10;
END
GO

-- ========== 2. Thêm lại 2 unique constraint đã khai báo trong entity nhưng thiếu ở DB ==========
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_users_username' AND object_id = OBJECT_ID('users'))
    ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_users_email' AND object_id = OBJECT_ID('users'))
    ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
GO

-- ========== 3. Index cho toàn bộ cột FK còn thiếu (30 cột) ==========
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cinema_products_cinema_id') CREATE INDEX ix_cinema_products_cinema_id ON cinema_products(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_cinema_products_product_id') CREATE INDEX ix_cinema_products_product_id ON cinema_products(product_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_favorites_movie_id') CREATE INDEX ix_favorites_movie_id ON favorites(movie_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_favorites_user_id') CREATE INDEX ix_favorites_user_id ON favorites(user_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_movie_genre_id') CREATE INDEX ix_movie_genre_id ON movie(genre_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_oder_details_food_order_online_id') CREATE INDEX ix_oder_details_food_order_online_id ON oder_details_food(order_online_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_oder_details_food_product_id') CREATE INDEX ix_oder_details_food_product_id ON oder_details_food(product_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_orders_online_cinema_id') CREATE INDEX ix_orders_online_cinema_id ON orders_online(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_orders_online_staff_id') CREATE INDEX ix_orders_online_staff_id ON orders_online(staff_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_orders_online_user_id') CREATE INDEX ix_orders_online_user_id ON orders_online(user_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_orders_online_user_vouchers_id') CREATE INDEX ix_orders_online_user_vouchers_id ON orders_online(user_vouchers_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_password_reset_tokens_user_id') CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens(user_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_points_histories_user_id') CREATE INDEX ix_points_histories_user_id ON points_histories(user_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_products_categories_products_id') CREATE INDEX ix_products_categories_products_id ON products(categories_products_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_promotions_cinema_id') CREATE INDEX ix_promotions_cinema_id ON promotions(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_promotions_movie_id') CREATE INDEX ix_promotions_movie_id ON promotions(movie_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_reviews_ticket_id') CREATE INDEX ix_reviews_ticket_id ON reviews(ticket_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_rooms_cinema_id') CREATE INDEX ix_rooms_cinema_id ON rooms(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_seats_room_id') CREATE INDEX ix_seats_room_id ON seats(room_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_seats_seat_type_id') CREATE INDEX ix_seats_seat_type_id ON seats(seat_type_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_showtimes_movie_id') CREATE INDEX ix_showtimes_movie_id ON showtimes(movie_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_showtimes_room_id') CREATE INDEX ix_showtimes_room_id ON showtimes(room_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_staff_cinema_id') CREATE INDEX ix_staff_cinema_id ON staff(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_staff_shifts_cinema_id') CREATE INDEX ix_staff_shifts_cinema_id ON staff_shifts(cinema_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_staff_shifts_staff_id') CREATE INDEX ix_staff_shifts_staff_id ON staff_shifts(staff_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_tickets_order_online_id') CREATE INDEX ix_tickets_order_online_id ON tickets(order_online_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_tickets_seat_id') CREATE INDEX ix_tickets_seat_id ON tickets(seat_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_tickets_showtime_id') CREATE INDEX ix_tickets_showtime_id ON tickets(showtime_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_user_vouchers_user_id') CREATE INDEX ix_user_vouchers_user_id ON user_vouchers(user_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_user_vouchers_voucher_id') CREATE INDEX ix_user_vouchers_voucher_id ON user_vouchers(voucher_id);
GO

-- ========== 4. Chống đặt trùng ghế ở tầng DB (chỉ 1 vé pending(0)/paid(1) cho mỗi ghế/suất chiếu) ==========
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uk_tickets_active_seat')
    CREATE UNIQUE INDEX uk_tickets_active_seat ON tickets(showtime_id, seat_id) WHERE status IN (0, 1);
GO

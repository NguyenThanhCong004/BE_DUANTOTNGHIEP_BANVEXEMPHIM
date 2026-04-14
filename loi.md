# 📋 TÀI LIỆU THỰC THỂ CHI TIẾT (DỰA TRÊN SƠ ĐỒ 1.PNG)

Tài liệu này đã được chuẩn hóa 100% theo sơ đồ thực thể `1.png`, sử dụng kiểu dữ liệu `Integer` cho ID và `camelCase` cho thuộc tính Java.

---

## 1. NHÓM NGƯỜI DÙNG & THÀNH VIÊN

### 1.1. MembershipRanks (Hạng thành viên)
```java
@Data @Entity @Table(name = "membership_ranks")
public class MembershipRank {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rank_id")
    private Integer rankId;
    @Column(name = "rank_name")
    private String rankName;
    @Column(name = "min_spending")
    private Double minSpending;
    private String description;
    @Column(name = "discount_percent")
    private Double discountPercent;
    @Column(name = "bonus_point")
    private Integer bonusPoint;
}
```

### 1.2. Users (Người dùng)
```java
@Data @Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;
    private String username;
    private String password;
    private String fullname;
    private Integer status;
    private LocalDate birthday;
    private String avatar;
    private String email;
    private String phone;
    private Integer points;
    @Column(name = "total_spending")
    private Double totalSpending;
    @ManyToOne @JoinColumn(name = "rank_id")
    private MembershipRank rank;
}
```

### 1.3. PointsHistories (Lịch sử điểm)
```java
@Data @Entity @Table(name = "points_histories")
public class PointsHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_history_id")
    private Integer pointHistoryId;
    private LocalDate date;
    private String description;
    private Integer points;
    @ManyToOne @JoinColumn(name = "user_id")
    private User user;
}
```

---

## 2. NHÓM RẠP & NHÂN VIÊN

### 2.1. Cinemas (Rạp chiếu)
```java
@Data @Entity @Table(name = "cinemas")
public class Cinema {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cinema_id")
    private Integer cinemaId;
    private String name;
    private String address;
    private Integer status;
}
```

### 2.2. Staff (Nhân viên)
```java
@Data @Entity @Table(name = "staff")
public class Staff {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Integer staffId;
    private String email;
    private String password;
    private String fullname;
    private Integer status;
    private String phone;
    private LocalDate birthday;
    private String role;
    private String avatar;
    @ManyToOne @JoinColumn(name = "cinema_id")
    private Cinema cinema;
}
```

### 2.3. StaffShifts (Ca làm việc)
```java
@Data @Entity @Table(name = "staff_shifts")
public class StaffShift {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_shifts_id")
    private Integer staffShiftId;
    @Column(name = "start_time")
    private LocalDateTime startTime;
    @Column(name = "end_time")
    private LocalDateTime endTime;
    private LocalDate date;
    @ManyToOne @JoinColumn(name = "staff_id")
    private Staff staff;
}
```

---

## 3. NHÓM PHIM & SUẤT CHIẾU

### 3.1. Genres (Thể loại)
```java
@Data @Entity @Table(name = "genres")
public class Genre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "genre_id")
    private Integer genreId;
    private String name;
}
```

### 3.2. Movie (Phim)
```java
@Data @Entity @Table(name = "movie")    
public class Movie {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Integer movieId;
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(columnDefinition = "TEXT")
    private String content;
    private Integer duration;
    private String author;
    private String nation;
    @Column(name = "release_date")
    private LocalDate releaseDate;
    @Column(name = "age_limit")
    private Integer ageLimit;
    private String poster;
    private String banner;
    private Integer status;
    @Column(name = "base_price")
    private Double basePrice;
    @ManyToOne @JoinColumn(name = "genre_id")
    private Genre genre;
}
```

### 3.3. Showtimes (Suất chiếu)
```java
@Data @Entity @Table(name = "showtimes")
public class Showtime {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showtime_id")
    private Integer showtimeId;
    @Column(name = "start_time")
    private LocalDateTime startTime;
    @Column(name = "VAT%")
    private Double vatPercent;
    @ManyToOne @JoinColumn(name = "movie_id")
    private Movie movie;
    @ManyToOne @JoinColumn(name = "room_id")
    private Room room;
}
```

---

## 4. NHÓM PHÒNG CHIẾU & GHẾ

### 4.1. Rooms (Phòng chiếu)
```java
@Data @Entity @Table(name = "rooms")
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Integer roomId;
    private String name;
    private Integer status;
    @ManyToOne @JoinColumn(name = "cinema_id")
    private Cinema cinema;
}
```

### 4.2. SeatType (Loại ghế)
```java
@Data @Entity @Table(name = "seatype")
public class SeatType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_type_id")
    private Integer seatTypeId;
    private String name;
    private Double surcharge;
}
```

### 4.3. Seats (Ghế ngồi)
```java
@Data @Entity @Table(name = "seats")
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Integer seatId;
    private String number;
    private String row;
    private Integer x;
    private Integer y;
    @ManyToOne @JoinColumn(name = "room_id")
    private Room room;
    @ManyToOne @JoinColumn(name = "seat_type_id")
    private SeatType seatType;
}
```

---

## 5. NHÓM ĐẶT VÉ & THANH TOÁN

### 5.1. Vouchers (Mã giảm giá)
```java
@Data @Entity @Table(name = "vouchers")
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vouchers_id")
    private Integer vouchersId;
    private String code;
    @Column(name = "discount_type")
    private String discountType;
    private Double value;
    @Column(name = "min_order_value")
    private Double minOrderValue;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(name = "point_voucher")
    private Integer pointVoucher;
    private Integer status;
}
```

### 5.2. UserVouchers (Voucher người dùng)
```java
@Data @Entity @Table(name = "user_vouchers")
public class UserVoucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_voucher_id")
    private Integer userVoucherId;
    private Integer status;
    @ManyToOne @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne @JoinColumn(name = "voucher_id")
    private Voucher voucher;
}
```

### 5.3. Orders_online (Đơn hàng Online)
```java
@Data @Entity @Table(name = "orders_online")
public class OrderOnline {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_online_id")
    private Integer orderOnlineId;
    @Column(name = "order_code")
    private String orderCode;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    @Column(name = "original_amount")
    private Double originalAmount;
    @Column(name = "discount_amount")
    private Double discountAmount;
    @Column(name = "final_amount")
    private Double finalAmount;
    private Integer status;
    @ManyToOne @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne @JoinColumn(name = "user_vouchers_id")
    private UserVoucher userVoucher;
}
```

### 5.4. Tickets (Vé xem phim)
```java
@Data @Entity @Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;
    private Integer status;
    private Double price;
    @ManyToOne @JoinColumn(name = "showtime_id")
    private Showtime showtime;
    @ManyToOne @JoinColumn(name = "seat_id")
    private Seat seat;
    @ManyToOne @JoinColumn(name = "order_online_id")
    private OrderOnline orderOnline;
}
```

---

## 6. NHÓM SẢN PHẨM & DỊCH VỤ

### 6.1. CategoriesProducts (Loại sản phẩm)
```java
@Data @Entity @Table(name = "categories_products")
public class CategoryProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categories_products_id")
    private Integer categoryProductId;
    private String name;
}
```

### 6.2. Products (Sản phẩm bắp nước)
```java
@Data @Entity @Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer productId;
    private String name;
    private String description;
    private Double price;
    private String image;
    private Integer status;
    @ManyToOne @JoinColumn(name = "categories_products_id")
    private CategoryProduct category;
}
```

### 6.3. CinemaProducts (Sản phẩm tại rạp)
```java
@Data @Entity @Table(name = "cinema_products")
public class CinemaProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cinema_products_id")
    private Integer cinemaProductId;
    @Column(name = "is_active")
    private Boolean isActive;
    @ManyToOne @JoinColumn(name = "cinema_id")
    private Cinema cinema;
    @ManyToOne @JoinColumn(name = "product_id")
    private Product product;
}
```

### 6.4. OderDetails_food (Chi tiết món ăn)
```java
@Data @Entity @Table(name = "oder_details_food")
public class OrderDetailFood {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oder_details_food_id")
    private Integer orderDetailFoodId;
    private Integer status;
    private Integer quantity;
    private Double price;
    @ManyToOne @JoinColumn(name = "order_online_id")
    private OrderOnline orderOnline;
    @ManyToOne @JoinColumn(name = "product_id")
    private Product product;
}
```

---

## 7. NHÓM TIỆN ÍCH & TƯƠNG TÁC

### 7.1. Favorites (Yêu thích)
```java
@Data @Entity @Table(name = "favorites")
public class Favorite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Integer favoriteId;
    @ManyToOne @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne @JoinColumn(name = "movie_id")
    private Movie movie;
}
```

### 7.2. Reviews (Đánh giá)
```java
@Data @Entity @Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Integer reviewId;
    private Integer rating;
    private String comment;
    @ManyToOne @JoinColumn(name = "ticket_id")
    private Ticket ticket;
}
```

### 7.3. Promotions (Khuyến mãi rạp/phim)
```java
@Data @Entity @Table(name = "promotions")
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Integer promotionId;
    @Column(name = "promotion_name")
    private String promotionName;
    @Column(name = "discount_percent")
    private Double discountPercent;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    private Integer status;
    @ManyToOne @JoinColumn(name = "movie_id")
    private Movie movie;
    @ManyToOne @JoinColumn(name = "cinema_id") // Admin rạp nào tạo khuyến mãi cho rạp đó
    private Cinema cinema;
}
```

### 7.4. News (Tin tức)
```java
@Data @Entity @Table(name = "news")
public class News {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Integer newsId;
    private String title;
    private String content;
    private String image;
    private Integer status;
}
```

---

## 8. GIẢI THÍCH CHI TIẾT CÁC CỘT QUAN TRỌNG (DATA DICTIONARY)

### MembershipRanks
- `min_spending`: Số tiền tối thiểu chi tiêu để đạt hạng.
- `discount_percent`: % giảm giá mặc định cho hạng thành viên.
- `bonus_point`: Hệ số cộng điểm thưởng.

### Users
- `points`: Điểm tích lũy hiện tại của khách.
- `total_spending`: Tổng chi tiêu (dùng để xét hạng thành viên).

### Movie & Showtimes
- `age_limit`: Độ tuổi tối thiểu xem phim.
- `base_price`: Giá vé gốc.
- `VAT%`: Thuế suất áp dụng cho suất chiếu.

### Vouchers
- `point_voucher`: Số điểm cần để đổi lấy voucher này.
- `min_order_value`: Giá trị đơn hàng tối thiểu để áp dụng.

### Orders_online
- `order_code`: Mã đơn hàng duy nhất (sinh mã QR).
- `final_amount`: Số tiền cuối cùng khách phải trả.

---

## 9. LUỒNG NGHIỆP VỤ 5 VAI TRÒ CHI TIẾT (BUSINESS WORKFLOWS)

 1. Luồng SuperAdmin (Hệ thống - Cấp cao nhất)
   * Quản trị Tài nguyên Gốc:
       - Nạp danh sách phim (Movie) và định nghĩa các thể loại (Genres).
       - Thiết lập danh mục sản phẩm bắp nước gốc (Products & CategoriesProducts).
   * Cấu hình Kinh doanh Toàn quốc:
       - Thiết lập các mốc chi tiêu và quyền lợi cho hạng thành viên (MembershipRanks).
       - Tạo các mã giảm giá (Vouchers) áp dụng cho mọi rạp.
       - Tạo các khuyến mãi lớn (Promotions) mang tính thương hiệu hệ thống (Cột cinema_id để NULL).
   * Vận hành Chuỗi:
       - Khởi tạo các rạp chiếu (Cinemas) và cấp quyền cho tài khoản Admin tại từng chi nhánh.
   * Phân tích Chiến lược:
       - Xem báo cáo doanh thu tổng hợp từ tất cả các rạp qua bảng Orders_online.

  ---


  2. Luồng Admin (Quản lý Chi nhánh/Rạp)
   * Thiết lập Cơ sở Hạ tầng:
       - Khởi tạo phòng chiếu (Rooms) và thiết kế sơ đồ ghế ngồi (Seats) theo loại ghế (SeatType) tại rạp của mình.
   * Điều phối Vận hành:
       - Sắp xếp lịch chiếu (Showtimes) cho các phim đang hot.
       - Quản lý kho bắp nước tại rạp: Chọn sản phẩm từ hệ thống và bật is_active trong CinemaProducts.
   * Quản lý Nhân sự:
       - Phân công ca làm việc cụ thể cho nhân viên thông qua StaffShifts.
   * Marketing Địa phương:
       - Khuyến mãi riêng: Tự tạo các chương trình Promotions giảm giá chỉ áp dụng tại rạp của mình (Gán cinema_id là ID rạp hiện 
         tại).
   * Giám sát: Theo dõi hiệu suất bán hàng của nhân viên và doanh thu tại chi nhánh.

  ---


  3. Luồng Staff (Nhân viên Quầy POS - Tốc độ cao)
   * Bán hàng tại quầy (Ticketing & F&B):
       - Luồng 1 (Mua vé): Chọn phim -> Chọn ghế -> Thanh toán -> In vé giấy (Tickets).
       - Luồng 2 (Mua bắp nước riêng): Khách chỉ mua bắp -> Tạo đơn hàng Orders_online -> Chỉ có dữ liệu trong OderDetails_food ->
         Thanh toán -> In hóa đơn.
   * Soát vé & Check-in:
       - Quét mã QR từ App khách hàng (order_code trong Orders_online) -> Xác nhận tình trạng thanh toán -> In vé cứng hoặc đánh  
         dấu khách đã vào rạp.
   * Quản lý ca làm việc:
       - Check-in/Check-out ca làm việc theo lịch StaffShifts. Báo cáo tồn kho bắp nước cuối ca.

  ---


  4. Luồng Customer (Khách hàng Thành viên - App/Web)
   * Hành trình Đặt chỗ (Booking Journey):
       - Xem tin tức (News) & Khuyến mãi (Promotions) -> Chọn phim -> Chọn rạp & suất chiếu -> Kéo thả chọn ghế (Seat) -> Chọn bắp
         nước kèm theo.
   * Thanh toán & Ưu đãi:
       - Đổi điểm tích lũy (points) lấy mã giảm giá (Vouchers).
       - Hệ thống tự động áp dụng discount_percent dựa trên hạng thành viên (MembershipRank).
       - Thanh toán qua PayOS -> Nhận mã QR đơn hàng (order_code).
   * Hậu mãi & Loyalty:
       - Sau khi xem phim: Hệ thống tự động cộng điểm vào PointsHistories và cập nhật total_spending để xét lên hạng.
       - Khách hàng viết Đánh giá (Reviews) cho phim hoặc lưu phim vào danh sách Yêu thích (Favorites).

  ---


  5. Luồng Guest (Khách vãn lai - Tiếp cận)
   * Tra cứu Thông tin:
       - Xem lịch chiếu phim, giá vé, danh sách rạp và các bài viết tin tức điện ảnh.
   * Trải nghiệm Thử:
       - Khám phá sơ đồ ghế ngồi thực tế của các phòng chiếu nhưng chưa được phép giữ chỗ.
   * Quyết định Chuyển đổi:
       - Thực hiện đăng ký tài khoản để nhận điểm thưởng chào mừng (bonus_point) và bắt đầu hưởng ưu đãi thành viên.

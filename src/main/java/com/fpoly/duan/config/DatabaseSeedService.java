package com.fpoly.duan.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.entity.CategoryProduct;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.CinemaProduct;
import com.fpoly.duan.entity.Genre;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.News;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.entity.Promotion;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.StaffShift;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.CategoryProductRepository;
import com.fpoly.duan.repository.CinemaProductRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.GenreRepository;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.NewsRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.PromotionRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.SeatTypeRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.repository.VoucherRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Nạp dữ liệu mẫu phong phú (mô phỏng website đang hoạt động) khi {@code app.data.seed=true}.
 * Chỉ chạy khi chưa có user username {@value #SEED_MARKER_USER}.
 */
@Slf4j
@Service
public class DatabaseSeedService {

    public static final String SEED_MARKER_USER = "khachdemo";

    private static final String USER_PW = "User@123";
    private static final String STAFF_PW = "Staff@123";

    private static final LocalTime[] SHOW_SLOTS = {
            LocalTime.of(9, 0),
            LocalTime.of(11, 30),
            LocalTime.of(14, 0),
            LocalTime.of(16, 30),
            LocalTime.of(19, 0),
            LocalTime.of(21, 30),
    };

    private static final int SHOWTIME_DAY_SPAN = 7;
    private static final String[] SEAT_ROWS = { "A", "B", "C", "D", "E", "F", "G", "H" };
    private static final int SEAT_COLS = 10;

    private final PasswordEncoder passwordEncoder;
    private final GenreRepository genreRepository;
    private final CinemaRepository cinemaRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final PromotionRepository promotionRepository;
    private final MembershipRankRepository membershipRankRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final CategoryProductRepository categoryProductRepository;
    private final ProductRepository productRepository;
    private final CinemaProductRepository cinemaProductRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final VoucherRepository voucherRepository;
    private final NewsRepository newsRepository;

    public DatabaseSeedService(
            PasswordEncoder passwordEncoder,
            GenreRepository genreRepository,
            CinemaRepository cinemaRepository,
            MovieRepository movieRepository,
            RoomRepository roomRepository,
            SeatTypeRepository seatTypeRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            PromotionRepository promotionRepository,
            MembershipRankRepository membershipRankRepository,
            UserRepository userRepository,
            StaffRepository staffRepository,
            StaffShiftRepository staffShiftRepository,
            CategoryProductRepository categoryProductRepository,
            ProductRepository productRepository,
            CinemaProductRepository cinemaProductRepository,
            OrderOnlineRepository orderOnlineRepository,
            VoucherRepository voucherRepository,
            NewsRepository newsRepository) {
        this.passwordEncoder = passwordEncoder;
        this.genreRepository = genreRepository;
        this.cinemaRepository = cinemaRepository;
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.promotionRepository = promotionRepository;
        this.membershipRankRepository = membershipRankRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.staffShiftRepository = staffShiftRepository;
        this.categoryProductRepository = categoryProductRepository;
        this.productRepository = productRepository;
        this.cinemaProductRepository = cinemaProductRepository;
        this.orderOnlineRepository = orderOnlineRepository;
        this.voucherRepository = voucherRepository;
        this.newsRepository = newsRepository;
    }

    @Transactional
    public void seedIfEnabled() {
        if (userRepository.existsByUsername(SEED_MARKER_USER)) {
            log.info("[DataSeed] Bỏ qua — đã có tài khoản mẫu '{}'.", SEED_MARKER_USER);
            return;
        }

        log.info("[DataSeed] Đang tạo dữ liệu mẫu (quy mô website đang hoạt động)...");

        // --- Hạng thành viên ---
        MembershipRank rankMember = membershipRankRepository.save(rank("MEMBER", 0, 0, 1, "Thành viên mới"));
        MembershipRank rankSilver = membershipRankRepository.save(rank("SILVER", 2_000_000, 5, 2, "Bạc"));
        MembershipRank rankGold = membershipRankRepository.save(rank("GOLD", 5_000_000, 10, 3, "Vàng"));

        // --- Thể loại ---
        Genre gHanhDong = genreRepository.save(buildGenre("Hành động"));
        Genre gHoatHinh = genreRepository.save(buildGenre("Hoạt hình"));
        Genre gTinhCam = genreRepository.save(buildGenre("Tình cảm"));
        Genre gKinhDi = genreRepository.save(buildGenre("Kinh dị"));
        Genre gVienTuong = genreRepository.save(buildGenre("Viễn tưởng"));

        // --- Rạp ---
        Cinema c1 = cinemaRepository.save(buildCinema("CineStar Landmark 81", "208 Nguyễn Hữu Cảnh, Q.Bình Thạnh, TP.HCM", 1));
        Cinema c2 = cinemaRepository.save(buildCinema("Galaxy Nguyễn Du", "116 Nguyễn Du, Q.1, TP.HCM", 1));
        Cinema c3 = cinemaRepository.save(buildCinema("Beta Cineplex Quang Trung", "645 Quang Trung, Q.Gò Vấp, TP.HCM", 1));

        // --- Phim (đang chiếu + sắp chiếu) ---
        LocalDate today = LocalDate.now();
        List<Movie> movies = new ArrayList<>();
        movies.add(movie("DUNE: PHẦN HAI", "Hành tinh Arrakis — phần tiếp theo.", gHanhDong, 166, 13,
                "https://wallpaperaccess.com/full/1561986.jpg", 95_000, today.minusMonths(1)));
        movies.add(movie("KUNG FU PANDA 4", "Po trở lại thung lũng.", gHoatHinh, 94, 0,
                "https://m.media-amazon.com/images/I/81QDUulWbVL.jpg", 75_000, today.minusWeeks(2)));
        movies.add(movie("MAI", "Phim Việt.", gTinhCam, 131, 18,
                "https://upload.wikimedia.org/wikipedia/vi/2/26/Mai_%28phim_2024%29_poster.jpg", 85_000, today.minusMonths(1)));
        movies.add(movie("INSIDE OUT 2", "Những mảnh cảm xúc mới.", gHoatHinh, 96, 0,
                "https://upload.wikimedia.org/wikipedia/en/f/f7/Inside_Out_2_poster.jpg", 80_000, today.minusWeeks(3)));
        movies.add(movie("A QUIET PLACE: DAY ONE", "Im lặng là sống còn.", gKinhDi, 99, 16,
                "https://upload.wikimedia.org/wikipedia/en/6/6c/A_Quiet_Place_Day_One_poster.jpg", 88_000, today.minusDays(10)));
        movies.add(movie("DEADPOOL & WOLVERINE", "Hành động hài MCU.", gHanhDong, 128, 18,
                "https://upload.wikimedia.org/wikipedia/en/4/4c/Deadpool_%26_Wolverine_poster.jpg", 92_000, today.minusWeeks(1)));
        movies.add(movie("TRANSFORMERS ONE", "Robot biến hình — phần mới.", gVienTuong, 104, 13,
                "https://upload.wikimedia.org/wikipedia/en/8/8e/Transformers_One_poster.jpg", 78_000, today.minusDays(5)));
        movies.add(movie("MOANA 2", "Hành trình đại dương.", gHoatHinh, 100, 0,
                "https://upload.wikimedia.org/wikipedia/en/8/8e/Moana_2_poster.jpg", 76_000, today.plusWeeks(2)));
        for (Movie mv : movies) {
            movieRepository.save(mv);
        }

        // --- Phòng (mỗi rạp 4 phòng) ---
        List<Room> allRooms = new ArrayList<>();
        for (Cinema c : List.of(c1, c2, c3)) {
            for (int i = 1; i <= 4; i++) {
                allRooms.add(roomRepository.save(buildRoom("Phòng " + i, 1, c)));
            }
        }

        // --- Loại ghế ---
        SeatType stThuong = seatTypeRepository.save(buildSeatType("Ghế thường", 0.0));
        SeatType stVip = seatTypeRepository.save(buildSeatType("Ghế VIP", 20_000.0));
        SeatType stDoi = seatTypeRepository.save(buildSeatType("Ghế đôi Sweetbox", 80_000.0));

        // --- Ghế cho mọi phòng ---
        for (Room room : allRooms) {
            seatRepository.saveAll(buildSeatsForRoom(room, stThuong, stVip, stDoi));
        }

        // --- Suất chiếu: 7 ngày × mọi phòng × 6 khung giờ (xoay vòng phim) ---
        int movieCursor = 0;
        for (int d = 0; d < SHOWTIME_DAY_SPAN; d++) {
            LocalDate day = today.plusDays(d);
            for (Room room : allRooms) {
                for (LocalTime t : SHOW_SLOTS) {
                    Movie mv = movies.get(movieCursor % movies.size());
                    showtimeRepository.save(buildShowtime(day.atTime(t), 8.0, mv, room));
                    movieCursor++;
                }
            }
        }

        // --- Khuyến mãi (nhiều kênh) ---
        promotionRepository.save(buildPromotion("Thứ 3 vui vẻ -10%", 10.0, today.minusDays(3), today.plusMonths(2), 1, movies.get(0), c1));
        promotionRepository.save(buildPromotion("HSSV giảm 15%", 15.0, today, today.plusMonths(1), 1, movies.get(1), c1));
        promotionRepository.save(buildPromotion("Galaxy Night 20%", 20.0, today.minusDays(1), today.plusMonths(1), 1, movies.get(3), c2));
        promotionRepository.save(buildPromotion("Beta cuối tuần", 12.0, today, today.plusMonths(2), 1, movies.get(4), c3));
        promotionRepository.save(buildPromotion("Combo 2 vé -8%", 8.0, today.minusWeeks(1), today.plusMonths(1), 1, movies.get(5), c2));
        promotionRepository.save(buildPromotion("Ưu đãi gia đình", 18.0, today, today.plusMonths(3), 1, movies.get(6), c1));

        // --- Loại sản phẩm & sản phẩm / combo (danh mục toàn hệ thống) ---
        CategoryProduct catCombo = categoryProductRepository.save(buildCategoryProduct("Combo bắp nước"));
        CategoryProduct catBap = categoryProductRepository.save(buildCategoryProduct("Bắp rang"));
        CategoryProduct catNuoc = categoryProductRepository.save(buildCategoryProduct("Nước & đồ uống"));
        CategoryProduct catSnack = categoryProductRepository.save(buildCategoryProduct("Snack"));

        List<Product> catalog = new ArrayList<>();
        String img = "https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=400";
        String imgDrink = "https://images.unsplash.com/photo-1544145942-f90425340c48?w=400";
        catalog.add(saveProduct("Combo Couple 1", "2 nước + 1 bắp vừa + snack", 189_000, img, catCombo));
        catalog.add(saveProduct("Combo Family", "4 nước + 2 bắp lớn", 329_000, img, catCombo));
        catalog.add(saveProduct("Combo Solo", "1 nước + 1 bắp nhỏ", 99_000, img, catCombo));
        catalog.add(saveProduct("Combo Premium", "Nước premium + bắp caramel", 219_000, img, catCombo));
        catalog.add(saveProduct("Bắp vị caramel", "Size M", 55_000, img, catBap));
        catalog.add(saveProduct("Bắp phô mai", "Size L", 65_000, img, catBap));
        catalog.add(saveProduct("Bắp mix vị", "Size M", 60_000, img, catBap));
        catalog.add(saveProduct("Bắp truyền thống", "Size S", 45_000, img, catBap));
        catalog.add(saveProduct("Coca-Cola", "Ly 500ml", 35_000, imgDrink, catNuoc));
        catalog.add(saveProduct("Fanta cam", "Ly 500ml", 35_000, imgDrink, catNuoc));
        catalog.add(saveProduct("Sprite", "Ly 500ml", 32_000, imgDrink, catNuoc));
        catalog.add(saveProduct("Nước suối", "500ml", 20_000, imgDrink, catNuoc));
        catalog.add(saveProduct("Trà đào", "Size M", 45_000, imgDrink, catNuoc));
        catalog.add(saveProduct("Kẹo socola", "Gói 80g", 35_000, img, catSnack));
        catalog.add(saveProduct("Snack khoai tây", "Gói 50g", 28_000, img, catSnack));
        catalog.add(saveProduct("Hạt điều", "Gói 40g", 42_000, img, catSnack));

        // --- Gắn sản phẩm theo rạp (cinema_products): một phần đang bán, một phần tắt, một phần chưa gắn ---
        seedCinemaProducts(c1, catalog, 13, 9);
        seedCinemaProducts(c2, catalog, 11, 7);
        seedCinemaProducts(c3, catalog, 15, 12);

        String userHash = passwordEncoder.encode(USER_PW);

        // --- User khách (nhiều tài khoản) ---
        User uDemo = userRepository.save(buildUser(SEED_MARKER_USER, "Khách Demo", "khachdemo@gmail.com", "0909123456",
                userHash, 520, 2_100_000.0, rankGold, today.minusYears(1)));
        User uMinh = userRepository.save(buildUser("minhkhang", "Minh Khang", "minhkhang@gmail.com", "0909234567",
                userHash, 80, 120_000.0, rankMember, today.minusMonths(2)));
        User uNga = userRepository.save(buildUser("thanhnga", "Thanh Nga", "thanhnga@gmail.com", "0909345678",
                userHash, 210, 890_000.0, rankSilver, today.minusMonths(6)));
        User uBao = userRepository.save(buildUser("quocbao", "Quốc Bảo", "quocbao@gmail.com", "0909456789",
                userHash, 45, 45_000.0, rankMember, today.minusWeeks(3)));
        User uChi = userRepository.save(buildUser("linhchi", "Linh Chi", "linhchi@gmail.com", "0909567890",
                userHash, 340, 3_400_000.0, rankGold, today.minusYears(2)));

        // --- Đơn online (hóa đơn admin) — nhiều trạng thái & thời gian ---
        LocalDateTime nowDt = LocalDateTime.now();
        seedOrderOnline("CT-ORD-1001", nowDt.minusMinutes(30), 220_000, 22_000, 198_000, 1, uDemo);
        seedOrderOnline("CT-ORD-1002", nowDt.minusHours(3), 180_000, 0, 180_000, 1, uMinh);
        seedOrderOnline("CT-ORD-1003", nowDt.minusDays(1).withHour(19).withMinute(15), 350_000, 35_000, 315_000, 1, uNga);
        seedOrderOnline("CT-ORD-1004", nowDt.minusDays(2).withHour(14), 95_000, 9_500, 85_500, 1, uBao);
        seedOrderOnline("CT-ORD-1005", nowDt.minusDays(3).withHour(21), 410_000, 50_000, 360_000, 1, uChi);
        seedOrderOnline("CT-ORD-1006", nowDt.minusDays(5).withHour(10), 150_000, 0, 150_000, 2, uDemo);
        seedOrderOnline("CT-ORD-1007", nowDt.minusDays(6).withHour(16), 200_000, 20_000, 180_000, 1, uMinh);
        seedOrderOnline("CT-ORD-1008", today.atTime(8, 0), 275_000, 27_500, 247_500, 1, uChi);
        seedOrderOnline("CT-ORD-1009", today.atTime(11, 45), 120_000, 0, 120_000, 0, uNga);
        seedOrderOnline("CT-ORD-1010", today.atTime(13, 20), 300_000, 30_000, 270_000, 1, uDemo);
        seedOrderOnline("CT-ORD-1011", today.minusDays(1).atTime(9, 0), 88_000, 0, 88_000, 1, uBao);
        seedOrderOnline("CT-ORD-1012", nowDt.minusHours(8), 195_000, 19_500, 175_500, 1, uChi);
        seedOrderOnline("CT-ORD-1013", nowDt.minusDays(4).withHour(20), 420_000, 42_000, 378_000, 2, uMinh);
        seedOrderOnline("CT-ORD-1014", nowDt.minusWeeks(1).withHour(18), 160_000, 16_000, 144_000, 1, uNga);
        seedOrderOnline("CT-ORD-1015", nowDt.minusMinutes(5), 99_000, 0, 99_000, 0, uBao);

        // --- Voucher hệ thống (khớp FE: PERCENTAGE | FIXED_AMOUNT) ---
        voucherRepository.save(buildVoucher("WELCOME10", "PERCENTAGE", 10.0, 80_000, today.minusMonths(1), today.plusMonths(6), 0, 1));
        voucherRepository.save(buildVoucher("SUMMER50K", "FIXED_AMOUNT", 50_000, 200_000, today, today.plusMonths(2), 0, 1));
        voucherRepository.save(buildVoucher("VIP15", "PERCENTAGE", 15.0, 150_000, today.minusWeeks(2), today.plusMonths(3), 0, 1));
        voucherRepository.save(buildVoucher("POINT200", "PERCENTAGE", 5.0, 0, today.minusDays(10), today.plusMonths(1), 200, 1));

        // --- Tin tức ---
        newsRepository.save(buildNews("Khai trương phòng chiếu IMAX mới tại Landmark 81",
                "Trải nghiệm hình ảnh sống động với công nghệ âm thanh Dolby Atmos…",
                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800", 1));
        newsRepository.save(buildNews("Tuần lễ phim Việt — giảm giá vé 20%",
                "Hàng loạt tác phẩm trong nước tham gia chương trình ưu đãi…",
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800", 1));
        newsRepository.save(buildNews("Combo bắp nước mùa hè chỉ từ 79k",
                "Áp dụng tại mọi rạp Cinetoon trên toàn quốc…",
                "https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=800", 1));
        newsRepository.save(buildNews("Hướng dẫn đặt vé online qua PayOS",
                "Thanh toán nhanh chóng, nhận mã QR ngay sau khi đặt…",
                "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=800", 1));

        String staffHash = passwordEncoder.encode(STAFF_PW);
        String av = "https://i.pravatar.cc/150?u=staff";

        Staff superAd = buildStaff("superadmin@gmail.com", "superadmin", "Super Admin", "SUPER_ADMIN", null, staffHash, av, "0901111111");
        Staff admin1 = buildStaff("adminrap@gmail.com", "adminrap", "Quản lý rạp Landmark", "ADMIN", c1, staffHash, av, "0902222222");
        Staff admin2 = buildStaff("admingalaxy@gmail.com", "admingalaxy", "Quản lý Galaxy Nguyễn Du", "ADMIN", c2, staffHash, av, "0902222233");
        Staff admin3 = buildStaff("adminbeta@gmail.com", "adminbeta", "Quản lý Beta Quang Trung", "ADMIN", c3, staffHash, av, "0902222244");

        staffRepository.save(superAd);
        staffRepository.save(admin1);
        staffRepository.save(admin2);
        staffRepository.save(admin3);

        // Nhân viên rạp 1
        Staff b1 = staffRepository.save(buildStaff("banve1@gmail.com", "banve1", "NV Bán vé (L81)", "Bán vé", c1, staffHash, av, "0903333331"));
        Staff s1 = staffRepository.save(buildStaff("soatve1@gmail.com", "soatve1", "NV Soát vé (L81)", "Soát vé", c1, staffHash, av, "0904444441"));
        Staff p1 = staffRepository.save(buildStaff("phucvu1@gmail.com", "phucvu1", "NV Phục vụ (L81)", "Phục vụ", c1, staffHash, av, "0905555551"));
        // Nhân viên rạp 2
        Staff b2 = staffRepository.save(buildStaff("banve2@gmail.com", "banve2", "NV Bán vé (Galaxy)", "Bán vé", c2, staffHash, av, "0903333332"));
        Staff s2 = staffRepository.save(buildStaff("soatve2@gmail.com", "soatve2", "NV Soát vé (Galaxy)", "Soát vé", c2, staffHash, av, "0904444442"));
        Staff p2 = staffRepository.save(buildStaff("phucvu2@gmail.com", "phucvu2", "NV Phục vụ (Galaxy)", "Phục vụ", c2, staffHash, av, "0905555552"));
        // Nhân viên rạp 3
        staffRepository.save(buildStaff("banve3@gmail.com", "banve3", "NV Bán vé (Beta)", "Bán vé", c3, staffHash, av, "0903333333"));
        staffRepository.save(buildStaff("soatve3@gmail.com", "soatve3", "NV Soát vé (Beta)", "Soát vé", c3, staffHash, av, "0904444443"));
        staffRepository.save(buildStaff("phucvu3@gmail.com", "phucvu3", "NV Phục vụ (Beta)", "Phục vụ", c3, staffHash, av, "0905555553"));

        // Ca làm (nhiều ngày, 2 khung giờ / ngày) — rạp 1
        seedShiftWeek(b1, s1, p1, today);
        seedShiftWeek(b2, s2, p2, today);

        long showCount = showtimeRepository.count();
        long seatCount = seatRepository.count();
        long orderCnt = orderOnlineRepository.count();
        long prodCnt = productRepository.count();

        log.info("[DataSeed] Hoàn tất. Khách: {} / {} | Staff: {} / {} | ~{} suất | ~{} ghế | {} đơn online | {} SP (catalog).",
                SEED_MARKER_USER, USER_PW, "*@gmail.com", STAFF_PW, showCount, seatCount, orderCnt, prodCnt);
    }

    private void seedCinemaProducts(Cinema cinema, List<Product> catalog, int linkedCount, int lastActiveIndexInclusive) {
        int n = Math.min(linkedCount, catalog.size());
        for (int i = 0; i < n; i++) {
            CinemaProduct cp = new CinemaProduct();
            cp.setCinema(cinema);
            cp.setProduct(catalog.get(i));
            cp.setIsActive(i <= lastActiveIndexInclusive);
            cinemaProductRepository.save(cp);
        }
    }

    private Product saveProduct(String name, String desc, double price, String image, CategoryProduct category) {
        return productRepository.save(buildProduct(name, desc, price, image, 1, category));
    }

    private static CategoryProduct buildCategoryProduct(String name) {
        CategoryProduct c = new CategoryProduct();
        c.setName(name);
        return c;
    }

    private static Product buildProduct(String name, String desc, double price, String image, int status, CategoryProduct category) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(desc);
        p.setPrice(price);
        p.setImage(image);
        p.setStatus(status);
        p.setCategory(category);
        return p;
    }

    private void seedOrderOnline(
            String orderCode,
            LocalDateTime createdAt,
            double originalAmount,
            double discountAmount,
            double finalAmount,
            int status,
            User user) {
        OrderOnline o = new OrderOnline();
        o.setOrderCode(orderCode);
        o.setCreatedAt(createdAt);
        o.setOriginalAmount(originalAmount);
        o.setDiscountAmount(discountAmount);
        o.setFinalAmount(finalAmount);
        o.setStatus(status);
        o.setUser(user);
        orderOnlineRepository.save(o);
    }

    private static Voucher buildVoucher(
            String code,
            String discountType,
            double value,
            double minOrder,
            LocalDate start,
            LocalDate end,
            int pointVoucher,
            int status) {
        Voucher v = new Voucher();
        v.setCode(code);
        v.setDiscountType(discountType);
        v.setValue(value);
        v.setMinOrderValue(minOrder);
        v.setStartDate(start);
        v.setEndDate(end);
        v.setPointVoucher(pointVoucher);
        v.setStatus(status);
        return v;
    }

    private static News buildNews(String title, String content, String image, int status) {
        News n = new News();
        n.setTitle(title);
        n.setContent(content);
        n.setImage(image);
        n.setStatus(status);
        return n;
    }

    private void seedShiftWeek(Staff banVe, Staff soatVe, Staff phucVu, LocalDate weekStart) {
        LocalTime[] blocks = { LocalTime.of(8, 0), LocalTime.of(14, 0) };
        for (int i = 0; i < 5; i++) {
            LocalDate d = weekStart.plusDays(i);
            for (LocalTime start : blocks) {
                LocalDateTime st = d.atTime(start);
                LocalDateTime en = st.plusHours(5);
                staffShiftRepository.save(buildShift(st, en, d, banVe));
                staffShiftRepository.save(buildShift(st, en, d, soatVe));
                staffShiftRepository.save(buildShift(st, en, d, phucVu));
            }
        }
    }

    private static MembershipRank rank(String name, double min, double disc, int bonus, String desc) {
        MembershipRank r = new MembershipRank();
        r.setRankName(name);
        r.setMinSpending(min);
        r.setDiscountPercent(disc);
        r.setBonusPoint(bonus);
        r.setDescription(desc);
        return r;
    }

    private static List<Seat> buildSeatsForRoom(Room room, SeatType thuong, SeatType vip, SeatType doi) {
        List<Seat> list = new ArrayList<>();
        for (int ri = 0; ri < SEAT_ROWS.length; ri++) {
            for (int n = 1; n <= SEAT_COLS; n++) {
                SeatType st;
                if (ri >= SEAT_ROWS.length - 1) {
                    st = doi;
                } else if (n >= SEAT_COLS - 2) {
                    st = vip;
                } else {
                    st = thuong;
                }
                Seat s = new Seat();
                s.setRow(SEAT_ROWS[ri]);
                s.setNumber(String.valueOf(n));
                s.setX(n);
                s.setY(ri + 1);
                s.setRoom(room);
                s.setSeatType(st);
                list.add(s);
            }
        }
        return list;
    }

    private static User buildUser(
            String username,
            String fullname,
            String email,
            String phone,
            String encPw,
            int points,
            double spending,
            MembershipRank rank,
            LocalDate birthday) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encPw);
        u.setFullname(fullname);
        u.setEmail(email);
        u.setPhone(phone);
        u.setStatus(1);
        u.setPoints(points);
        u.setTotalSpending(spending);
        u.setRank(rank);
        u.setBirthday(birthday);
        u.setAvatar("https://i.pravatar.cc/150?u=" + username);
        return u;
    }

    private Movie movie(String title, String desc, Genre genre, int duration, int age, String poster, double price, LocalDate release) {
        Movie m = new Movie();
        m.setTitle(title);
        m.setDescription(desc);
        m.setContent(desc);
        m.setDuration(duration);
        m.setAuthor("Studio");
        m.setNation("Nhiều quốc gia");
        m.setReleaseDate(release);
        m.setAgeLimit(age);
        m.setPoster(poster);
        m.setBanner(poster);
        m.setStatus(1);
        m.setBasePrice(price);
        m.setGenre(genre);
        return m;
    }

    private static Genre buildGenre(String name) {
        Genre g = new Genre();
        g.setName(name);
        return g;
    }

    private static Cinema buildCinema(String name, String address, int status) {
        Cinema c = new Cinema();
        c.setName(name);
        c.setAddress(address);
        c.setStatus(status);
        return c;
    }

    private static Room buildRoom(String name, int status, Cinema cinema) {
        Room r = new Room();
        r.setName(name);
        r.setStatus(status);
        r.setCinema(cinema);
        return r;
    }

    private static SeatType buildSeatType(String name, double surcharge) {
        SeatType st = new SeatType();
        st.setName(name);
        st.setSurcharge(surcharge);
        return st;
    }

    private static Showtime buildShowtime(LocalDateTime start, double vat, Movie movie, Room room) {
        Showtime s = new Showtime();
        s.setStartTime(start);
        s.setVatPercent(vat);
        s.setMovie(movie);
        s.setRoom(room);
        return s;
    }

    private static Promotion buildPromotion(
            String name,
            double pct,
            LocalDate start,
            LocalDate end,
            int status,
            Movie movie,
            Cinema cinema) {
        Promotion p = new Promotion();
        p.setPromotionName(name);
        p.setDiscountPercent(pct);
        p.setStartDate(start);
        p.setEndDate(end);
        p.setStatus(status);
        p.setMovie(movie);
        p.setCinema(cinema);
        return p;
    }

    private static Staff buildStaff(
            String email,
            String username,
            String fullname,
            String role,
            Cinema cinema,
            String encodedPassword,
            String avatar,
            String phone10) {
        Staff s = new Staff();
        s.setEmail(email);
        s.setUsername(username);
        s.setFullname(fullname);
        s.setRole(role);
        s.setCinema(cinema);
        s.setPassword(encodedPassword);
        s.setAvatar(avatar);
        s.setPhone(phone10);
        s.setBirthday(LocalDate.of(1995, 5, 20));
        s.setStatus(1);
        return s;
    }

    private static StaffShift buildShift(LocalDateTime start, LocalDateTime end, LocalDate date, Staff staff) {
        StaffShift sh = new StaffShift();
        sh.setStartTime(start);
        sh.setEndTime(end);
        sh.setDate(date);
        sh.setStaff(staff);
        return sh;
    }
}

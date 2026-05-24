package com.fpoly.duan.config;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.entity.CategoryProduct;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Favorite;
import com.fpoly.duan.entity.Genre;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.PointsHistory;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.entity.UserVoucher;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.CategoryProductRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.GenreRepository;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.PointsHistoryRepository;
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.SeatTypeRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.repository.VoucherRepository;
import com.fpoly.duan.repository.FavoriteRepository;
import com.fpoly.duan.util.SeatTypeNaming;

import lombok.extern.slf4j.Slf4j;

/**
 * Seed tối thiểu cho Docker/dev khi {@code app.data.seed=true}.
 */
@Slf4j
@Service
public class DatabaseSeedService {

    @Value("${app.seed.admin.username:admin}")
    private String adminUsername;

    @Value("${app.seed.admin.email:admin@fpoly.local}")
    private String adminEmail;

    @Value("${app.seed.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.seed.admin.fullname:System Admin}")
    private String adminFullname;

    @Value("${app.seed.admin.phone:0901111111}")
    private String adminPhone;

    private final PasswordEncoder passwordEncoder;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final MembershipRankRepository membershipRankRepository;
    private final CinemaRepository cinemaRepository;
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final VoucherRepository voucherRepository;
    private final CategoryProductRepository categoryProductRepository;
    private final ProductRepository productRepository;
    private final RoomRepository roomRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final FavoriteRepository favoriteRepository;
    private final PointsHistoryRepository pointsHistoryRepository;
    private final UserVoucherRepository userVoucherRepository;

    public DatabaseSeedService(
            PasswordEncoder passwordEncoder,
            StaffRepository staffRepository,
            UserRepository userRepository,
            MembershipRankRepository membershipRankRepository,
            CinemaRepository cinemaRepository,
            GenreRepository genreRepository,
            MovieRepository movieRepository,
            VoucherRepository voucherRepository,
            CategoryProductRepository categoryProductRepository,
            ProductRepository productRepository,
            RoomRepository roomRepository,
            SeatTypeRepository seatTypeRepository,
            SeatRepository seatRepository,
            ShowtimeRepository showtimeRepository,
            OrderOnlineRepository orderOnlineRepository,
            TicketRepository ticketRepository,
            OrderDetailFoodRepository orderDetailFoodRepository,
            FavoriteRepository favoriteRepository,
            PointsHistoryRepository pointsHistoryRepository,
            UserVoucherRepository userVoucherRepository) {
        this.passwordEncoder = passwordEncoder;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.membershipRankRepository = membershipRankRepository;
        this.cinemaRepository = cinemaRepository;
        this.genreRepository = genreRepository;
        this.movieRepository = movieRepository;
        this.voucherRepository = voucherRepository;
        this.categoryProductRepository = categoryProductRepository;
        this.productRepository = productRepository;
        this.roomRepository = roomRepository;
        this.seatTypeRepository = seatTypeRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.orderOnlineRepository = orderOnlineRepository;
        this.ticketRepository = ticketRepository;
        this.orderDetailFoodRepository = orderDetailFoodRepository;
        this.favoriteRepository = favoriteRepository;
        this.pointsHistoryRepository = pointsHistoryRepository;
        this.userVoucherRepository = userVoucherRepository;
    }

    @Transactional
    public void seedIfEnabled() {
        ensureDefaultSeatTypes();
        ensureDefaultGenres();
        seedMembershipRanks();
        backfillMissingMembershipRankStatus();
        seedStaffAccounts();
        backfillMissingUserRanks();
        log.info("[DataSeed] Seed tối thiểu đã hoàn tất.");
    }

    private void backfillMissingMembershipRankStatus() {
        List<MembershipRank> missingStatusRanks = membershipRankRepository.findAll().stream()
                .filter(r -> r.getStatus() == null)
                .toList();
        if (missingStatusRanks.isEmpty()) {
            return;
        }
        missingStatusRanks.forEach(r -> r.setStatus(1));
        membershipRankRepository.saveAll(missingStatusRanks);
        log.info("[DataSeed] Đã cập nhật status mặc định cho {} hạng thành viên.", missingStatusRanks.size());
    }

    private void seedCinemas() {
        if (cinemaRepository.count() > 0) {
            log.info("[DataSeed] Bảng rạp đã có dữ liệu -> bỏ qua.");
            return;
        }

        Cinema c1 = new Cinema();
        c1.setName("CineStar Quận 1");
        c1.setAddress("135 Hai Bà Trưng, Quận 1, TP.HCM");
        c1.setStatus(1);

        Cinema c2 = new Cinema();
        c2.setName("CineStar Thủ Đức");
        c2.setAddress("02 Võ Văn Ngân, TP. Thủ Đức, TP.HCM");
        c2.setStatus(1);

        cinemaRepository.saveAll(List.of(c1, c2));
        log.info("[DataSeed] Đã tạo dữ liệu mẫu rạp chiếu.");
    }

    private void seedGenresAndMovies() {
        if (movieRepository.count() > 0) {
            log.info("[DataSeed] Bảng phim đã có dữ liệu -> bỏ qua.");
            return;
        }

        Map<String, Genre> byName = new HashMap<>();
        if (genreRepository.count() == 0) {
            Genre action = new Genre();
            action.setName("Hành động");
            Genre romance = new Genre();
            romance.setName("Tình cảm");
            Genre horror = new Genre();
            horror.setName("Kinh dị");
            genreRepository.saveAll(List.of(action, romance, horror));
        }
        for (Genre g : genreRepository.findAll()) {
            byName.put(g.getName(), g);
        }

        Movie m1 = new Movie();
        m1.setTitle("Đêm Cuối Ở Sài Gòn");
        m1.setDescription("Bộ phim hành động pha yếu tố trinh thám.");
        m1.setContent("Nội dung phim mẫu để kiểm thử trang chi tiết.");
        m1.setDuration(125);
        m1.setAuthor("Đạo diễn A");
        m1.setNation("Việt Nam");
        m1.setReleaseDate(LocalDate.now().minusDays(20));
        m1.setAgeLimit(16);
        m1.setPoster("https://picsum.photos/400/600?random=11");
        m1.setBanner("https://picsum.photos/1200/500?random=11");
        m1.setStatus(1);
        m1.setBasePrice(90000.0);
        m1.setGenre(byName.getOrDefault("Hành động", null));

        Movie m2 = new Movie();
        m2.setTitle("Nắng Sau Cơn Mưa");
        m2.setDescription("Bộ phim tình cảm nhẹ nhàng.");
        m2.setContent("Nội dung phim mẫu để test home và booking.");
        m2.setDuration(110);
        m2.setAuthor("Đạo diễn B");
        m2.setNation("Việt Nam");
        m2.setReleaseDate(LocalDate.now().minusDays(10));
        m2.setAgeLimit(13);
        m2.setPoster("https://picsum.photos/400/600?random=12");
        m2.setBanner("https://picsum.photos/1200/500?random=12");
        m2.setStatus(1);
        m2.setBasePrice(85000.0);
        m2.setGenre(byName.getOrDefault("Tình cảm", null));

        movieRepository.saveAll(List.of(m1, m2));
        log.info("[DataSeed] Đã tạo dữ liệu mẫu phim và thể loại.");
    }

    private void seedVoucherTemplates() {
        if (voucherRepository.count() > 0) {
            log.info("[DataSeed] Bảng voucher đã có dữ liệu -> bỏ qua.");
            return;
        }

        Voucher v1 = new Voucher();
        v1.setCode("WELCOME10");
        v1.setDiscountType("PERCENT");
        v1.setValue(10.0);
        v1.setMinOrderValue(120000.0);
        v1.setMaxDiscountAmount(30000.0);
        v1.setStartDate(LocalDate.now().minusDays(1));
        v1.setEndDate(LocalDate.now().plusMonths(2));
        v1.setPointVoucher(150);
        v1.setStatus(1);

        Voucher v2 = new Voucher();
        v2.setCode("COMBO30K");
        v2.setDiscountType("AMOUNT");
        v2.setValue(30000.0);
        v2.setMinOrderValue(180000.0);
        v2.setMaxDiscountAmount(30000.0);
        v2.setStartDate(LocalDate.now().minusDays(1));
        v2.setEndDate(LocalDate.now().plusMonths(2));
        v2.setPointVoucher(250);
        v2.setStatus(1);

        voucherRepository.saveAll(List.of(v1, v2));
        log.info("[DataSeed] Đã tạo dữ liệu mẫu voucher.");
    }

    private void seedProductCatalog() {
        if (productRepository.count() > 0) {
            log.info("[DataSeed] Bảng sản phẩm đã có dữ liệu -> bỏ qua.");
            return;
        }

        CategoryProduct food;
        CategoryProduct drink;
        if (categoryProductRepository.count() == 0) {
            food = new CategoryProduct();
            food.setName("Đồ ăn");
            drink = new CategoryProduct();
            drink.setName("Nước uống");
            categoryProductRepository.saveAll(List.of(food, drink));
        } else {
            List<CategoryProduct> categories = categoryProductRepository.findAll();
            food = categories.get(0);
            drink = categories.size() > 1 ? categories.get(1) : categories.get(0);
        }

        Product p1 = new Product();
        p1.setName("Combo Bắp Nước Lớn");
        p1.setDescription("Bắp caramel + 1 nước ngọt size L");
        p1.setPrice(79000.0);
        p1.setImage("https://picsum.photos/300/300?random=21");
        p1.setStatus(1);
        p1.setCategory(food);

        Product p2 = new Product();
        p2.setName("Pepsi Lon");
        p2.setDescription("Nước ngọt Pepsi 330ml");
        p2.setPrice(25000.0);
        p2.setImage("https://picsum.photos/300/300?random=22");
        p2.setStatus(1);
        p2.setCategory(drink);

        productRepository.saveAll(List.of(p1, p2));
        log.info("[DataSeed] Đã tạo dữ liệu mẫu danh mục & sản phẩm.");
    }

    private void seedMembershipRanks() {
        if (membershipRankRepository.count() > 0) {
            log.info("[DataSeed] Bảng hạng thành viên đã có dữ liệu -> bỏ qua.");
            return;
        }

        MembershipRank defaultRank = new MembershipRank();
        defaultRank.setRankName("Hạng Đồng");
        defaultRank.setMinSpending(0.0);
        defaultRank.setDescription("Hạng mặc định cho thành viên mới.");
        defaultRank.setDiscountPercent(0.0);
        defaultRank.setBonusPoint(1);
        defaultRank.setStatus(1);

        membershipRankRepository.save(defaultRank);
        log.info("[DataSeed] Đã tạo hạng thành viên mặc định.");
    }

    private void ensureDefaultGenres() {
        if (genreRepository.count() > 0) {
            return;
        }
        Genre other = new Genre();
        other.setName("Khác");
        genreRepository.save(other);
        log.info("[DataSeed] Đã tạo thể loại phim mặc định: Khác.");
    }

    private void seedStaffAccounts() {
        String username = normalize(adminUsername, "admin");
        String email = normalize(adminEmail, "admin@fpoly.local");
        String fullname = normalize(adminFullname, "System Admin");
        String phone = normalize(adminPhone, "0901111111");
        String password = normalize(adminPassword, "Admin@123");

        if (Boolean.TRUE.equals(staffRepository.existsByUsername(username))
                || Boolean.TRUE.equals(staffRepository.existsByEmail(email))) {
            log.info("[DataSeed] Tài khoản admin đã tồn tại -> bỏ qua.");
            return;
        }

        Staff admin = new Staff();
        admin.setEmail(email);
        admin.setUsername(username);
        admin.setFullname(fullname);
        admin.setRole("SUPER_ADMIN");
        admin.setPassword(passwordEncoder.encode(password));
        admin.setAvatar("https://i.pravatar.cc/150?u=" + username);
        admin.setPhone(phone);
        admin.setBirthday(LocalDate.of(1995, 5, 20));
        admin.setStatus(1);
        staffRepository.save(admin);
        log.info("[DataSeed] Đã tạo tài khoản admin mặc định: {} / {}", username, password);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void seedUserAccounts() {
        Integer defaultRankId = getDefaultRankId();
        if (!Boolean.TRUE.equals(userRepository.existsByUsername("user01"))) {
            User user = new User();
            user.setUsername("user01");
            user.setPassword(passwordEncoder.encode("User01@123"));
            user.setFullname("Khách Hàng 01");
            user.setEmail("user01@gmail.com");
            user.setPhone("0901111121");
            user.setBirthday(LocalDate.of(2000, 1, 10));
            user.setAvatar("https://i.pravatar.cc/150?u=user01");
            user.setStatus(1);
            user.setPoints(120);
            user.setRankId(defaultRankId);
            user.setTotalSpending(0.0);
            userRepository.save(user);
            log.info("[DataSeed] Đã tạo user test: user01 / User01@123");
        }

        if (!Boolean.TRUE.equals(userRepository.existsByUsername("user02"))) {
            User user = new User();
            user.setUsername("user02");
            user.setPassword(passwordEncoder.encode("User02@123"));
            user.setFullname("Khách Hàng 02");
            user.setEmail("user02@gmail.com");
            user.setPhone("0901111122");
            user.setBirthday(LocalDate.of(1999, 11, 5));
            user.setAvatar("https://i.pravatar.cc/150?u=user02");
            user.setStatus(1);
            user.setPoints(80);
            user.setRankId(defaultRankId);
            user.setTotalSpending(0.0);
            userRepository.save(user);
            log.info("[DataSeed] Đã tạo user test: user02 / User02@123");
        }
    }

    private void backfillMissingUserRanks() {
        Integer defaultRankId = getDefaultRankId();
        if (defaultRankId == null) {
            return;
        }
        List<User> usersMissingRank = userRepository.findAll().stream()
                .filter(u -> u.getRankId() == null)
                .toList();
        if (usersMissingRank.isEmpty()) {
            return;
        }
        usersMissingRank.forEach(u -> u.setRankId(defaultRankId));
        userRepository.saveAll(usersMissingRank);
        log.info("[DataSeed] Đã cập nhật rank mặc định cho {} user chưa có rank_id.", usersMissingRank.size());
    }

    private Integer getDefaultRankId() {
        return membershipRankRepository.findAll().stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .map(MembershipRank::getRankId)
                .orElse(null);
    }

    /** Tạo loại ghế Thường / VIP / Đôi nếu chưa có (theo tên), mỗi lần chạy seed. */
    private void ensureDefaultSeatTypes() {
        upsertSeatTypeIfMissing("Thường", 0.0, false, "#0D6EFD");
        upsertSeatTypeIfMissing("VIP", 30_000.0, false, "#FFC107");
        upsertSeatTypeIfMissing("Đôi", 20_000.0, true, "#DC3545");
    }

    private void upsertSeatTypeIfMissing(String name, double surcharge, boolean coupleSeat, String colorHex) {
        Optional<SeatType> existing = seatTypeRepository.findByName(name);
        if (existing.isPresent()) {
            return;
        }
        SeatType t = new SeatType();
        t.setName(name);
        t.setSurcharge(surcharge);
        t.setCoupleSeat(coupleSeat);
        t.setColor(SeatTypeNaming.normalizeColorHex(colorHex));
        seatTypeRepository.save(t);
        log.info("[DataSeed] Đã tạo loại ghế: {} (phụ thu {}) couple={} color={}", name, surcharge, coupleSeat, colorHex);
    }

    private void seedRoomsSeatsAndShowtimes() {
        if (showtimeRepository.count() > 0 || roomRepository.count() > 0) {
            log.info("[DataSeed] Đã có phòng/suất chiếu -> bỏ qua.");
            return;
        }

        List<Cinema> cinemas = cinemaRepository.findAll();
        if (cinemas.isEmpty() || movieRepository.count() == 0) {
            return;
        }
        Cinema cinema = cinemas.get(0);

        Room room = new Room();
        room.setName("Phòng 1");
        room.setStatus(1);
        room.setCinema(cinema);
        room = roomRepository.save(room);

        ensureDefaultSeatTypes();
        SeatType thuong = seatTypeRepository.findByName("Thường")
                .orElseThrow(() -> new IllegalStateException("Thiếu loại ghế Thường sau seed"));
        SeatType vip = seatTypeRepository.findByName("VIP").orElse(thuong);
        SeatType doi = seatTypeRepository.findByName("Đôi").orElse(thuong);

        String[] rows = { "A", "B", "C" };
        SeatType[] typesPerRow = { thuong, vip, doi };
        for (int ri = 0; ri < rows.length; ri++) {
            String rowName = rows[ri];
            SeatType rowType = typesPerRow[ri];
            for (int i = 1; i <= 4; i++) {
                Seat s = new Seat();
                s.setRow(rowName);
                s.setNumber(String.valueOf(i));
                s.setX(i);
                s.setY(ri + 1);
                s.setStatus("1");
                s.setRoom(room);
                s.setSeatType(rowType);
                seatRepository.save(s);
            }
        }

        List<Movie> movies = movieRepository.findAll();
        Movie movie1 = movies.get(0);
        Movie movie2 = movies.size() > 1 ? movies.get(1) : movies.get(0);

        Showtime st1 = new Showtime();
        st1.setMovie(movie1);
        st1.setRoom(room);
        st1.setStartTime(java.time.LocalDateTime.now().plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0));
        st1.setSurcharge(0.0);

        Showtime st2 = new Showtime();
        st2.setMovie(movie2);
        st2.setRoom(room);
        st2.setStartTime(java.time.LocalDateTime.now().plusDays(2).withHour(20).withMinute(30).withSecond(0).withNano(0));
        st2.setSurcharge(10000.0);

        showtimeRepository.saveAll(List.of(st1, st2));
        log.info("[DataSeed] Đã tạo dữ liệu mẫu phòng/ghế/suất chiếu.");
    }

    private void seedUserActivitiesAndOrders() {
        if (orderOnlineRepository.count() > 0) {
            log.info("[DataSeed] Đã có đơn hàng online -> bỏ qua seed giao dịch mẫu.");
            return;
        }

        User user = userRepository.findByUsername("user01").orElse(null);
        Showtime showtime = showtimeRepository.findAll().stream().findFirst().orElse(null);
        List<Seat> seats = seatRepository.findAll();
        List<Product> products = productRepository.findAll();
        Voucher voucher = voucherRepository.findAll().stream().findFirst().orElse(null);
        Movie movie = movieRepository.findAll().stream().findFirst().orElse(null);
        if (user == null || showtime == null || seats.isEmpty()) {
            return;
        }

        OrderOnline order = new OrderOnline();
        order.setOrderCode("SEED-" + System.currentTimeMillis());
        order.setCreatedAt(java.time.LocalDateTime.now().minusDays(2));
        order.setOriginalAmount(210000.0);
        order.setDiscountAmount(20000.0);
        order.setFinalAmount(190000.0);
        order.setStatus(1);
        order.setUser(user);
        order = orderOnlineRepository.save(order);

        Ticket t1 = new Ticket();
        t1.setOrderOnline(order);
        t1.setShowtime(showtime);
        t1.setSeat(seats.get(0));
        t1.setPrice(95000.0);
        t1.setStatus(1);
        Ticket t2 = new Ticket();
        t2.setOrderOnline(order);
        t2.setShowtime(showtime);
        t2.setSeat(seats.size() > 1 ? seats.get(1) : seats.get(0));
        t2.setPrice(95000.0);
        t2.setStatus(1);
        ticketRepository.saveAll(List.of(t1, t2));

        if (!products.isEmpty()) {
            OrderDetailFood food = new OrderDetailFood();
            food.setOrderOnline(order);
            food.setProduct(products.get(0));
            food.setQuantity(1);
            food.setPrice(products.get(0).getPrice());
            food.setStatus(1);
            orderDetailFoodRepository.save(food);
        }

        user.setTotalSpending((user.getTotalSpending() != null ? user.getTotalSpending() : 0.0) + order.getFinalAmount());
        user.setPoints((user.getPoints() != null ? user.getPoints() : 0) + 19);
        userRepository.save(user);

        PointsHistory pointPlus = new PointsHistory();
        pointPlus.setUser(user);
        pointPlus.setDate(LocalDate.now().minusDays(2));
        pointPlus.setDescription("Tích điểm từ đơn " + order.getOrderCode());
        pointPlus.setPoints(19);
        pointsHistoryRepository.save(pointPlus);

        if (movie != null && !favoriteRepository.existsByUser_UserIdAndMovie_MovieId(user.getUserId(), movie.getMovieId())) {
            Favorite fav = new Favorite();
            fav.setUser(user);
            fav.setMovie(movie);
            favoriteRepository.save(fav);
        }

        if (voucher != null && !userVoucherRepository.existsByUser_UserIdAndVoucher_VouchersIdAndStatus(user.getUserId(),
                voucher.getVouchersId(), 1)) {
            UserVoucher uv = new UserVoucher();
            uv.setUser(user);
            uv.setVoucher(voucher);
            uv.setStatus(1);
            userVoucherRepository.save(uv);
        }

        log.info("[DataSeed] Đã tạo dữ liệu mẫu giao dịch/yêu thích/điểm cho user01.");
    }
}

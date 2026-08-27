package com.fpoly.duan.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.VoucherDTO;
import com.fpoly.duan.dto.me.FavoriteMovieIdRequest;
import com.fpoly.duan.dto.me.MeFavoriteMovieDto;
import com.fpoly.duan.dto.me.MeMovieReviewDto;
import com.fpoly.duan.dto.me.MeMovieReviewStatusDto;
import com.fpoly.duan.dto.me.MePointsHistoryDto;
import com.fpoly.duan.dto.me.MeTransactionDto;
import com.fpoly.duan.dto.me.MeTransactionItemDto;
import com.fpoly.duan.dto.me.MeUserVoucherRowDto;
import com.fpoly.duan.dto.me.MovieReviewRequest;
import com.fpoly.duan.dto.me.PagedResponse;
import com.fpoly.duan.entity.Favorite;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.PointsHistory;
import com.fpoly.duan.entity.Product;
import com.fpoly.duan.entity.Review;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.entity.UserVoucher;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.FavoriteRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.PointsHistoryRepository;
import com.fpoly.duan.repository.ReviewRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.repository.VoucherRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerMeService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final PointsHistoryRepository pointsHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;
    private final TicketQrService ticketQrService;

    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Phân trang thật ở DB: "Giao dịch" gộp 2 nguồn (đơn hàng + sự kiện điểm) — lấy đúng
     * (source_type,id) của 1 trang qua UNION ALL native query (xem OrderOnlineRepository), rồi chỉ
     * fetch chi tiết vé/bắp nước cho ĐÚNG số đơn trong trang đó (không phải toàn bộ lịch sử).
     * Không đánh dấu @Transactional readOnly vì bước backfill QR bên dưới cần ghi (saveAll).
     */
    public PagedResponse<MeTransactionDto> listTransactions(Integer userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = safePage * safeSize;

        List<Object[]> refs = orderOnlineRepository.findTransactionPageRefs(userId, offset, safeSize);
        long total = orderOnlineRepository.countTransactionRefs(userId);

        List<Integer> orderIds = new ArrayList<>();
        List<Integer> pointIds = new ArrayList<>();
        for (Object[] ref : refs) {
            Integer id = ((Number) ref[1]).intValue();
            if ("ORDER".equals(ref[0])) {
                orderIds.add(id);
            } else {
                pointIds.add(id);
            }
        }

        Map<Integer, OrderOnline> ordersById = orderIds.isEmpty() ? Map.of()
                : orderOnlineRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(OrderOnline::getOrderOnlineId, o -> o));
        Map<Integer, PointsHistory> pointsById = pointIds.isEmpty() ? Map.of()
                : pointsHistoryRepository.findAllById(pointIds).stream()
                        .collect(Collectors.toMap(PointsHistory::getPointHistoryId, p -> p));

        // Lấy vé + bắp nước CHỈ cho các đơn trong trang này (không phải toàn bộ lịch sử) — đây chính
        // là phần giảm tải so với trước (từng tải hết vé/bắp nước của mọi đơn dù chỉ hiển thị 10 dòng).
        Map<Integer, List<Ticket>> ticketsByOrder = orderIds.isEmpty() ? Map.of()
                : ticketRepository.findByOrderOnline_OrderOnlineIdInWithDetails(orderIds).stream()
                        .collect(Collectors.groupingBy(t -> t.getOrderOnline().getOrderOnlineId()));
        Map<Integer, List<OrderDetailFood>> foodsByOrder = orderIds.isEmpty() ? Map.of()
                : orderDetailFoodRepository.findByOrderOnline_OrderOnlineIdInWithProduct(orderIds).stream()
                        .collect(Collectors.groupingBy(f -> f.getOrderOnline().getOrderOnlineId()));

        List<Ticket> ticketsMissingQr = ticketsByOrder.values().stream().flatMap(List::stream)
                .filter(t -> t.getTicketCode() == null || t.getTicketCode().isBlank()
                        || t.getQrToken() == null || t.getQrToken().isBlank())
                .collect(Collectors.toList());
        if (!ticketsMissingQr.isEmpty()) {
            ticketQrService.assignSecureCodes(ticketsMissingQr);
            ticketRepository.saveAll(ticketsMissingQr);
        }

        // Ghép DTO theo ĐÚNG thứ tự trả về của UNION query (findAllById không giữ thứ tự truyền vào).
        List<MeTransactionDto> content = new ArrayList<>();
        for (Object[] ref : refs) {
            Integer id = ((Number) ref[1]).intValue();
            if ("ORDER".equals(ref[0])) {
                OrderOnline o = ordersById.get(id);
                if (o != null) {
                    content.add(orderToTransaction(o,
                            ticketsByOrder.getOrDefault(id, List.of()),
                            foodsByOrder.getOrDefault(id, List.of())));
                }
            } else {
                PointsHistory ph = pointsById.get(id);
                if (ph != null) {
                    content.add(pointsToTransaction(ph));
                }
            }
        }
        return PagedResponse.of(content, safePage, safeSize, total);
    }

    private MeTransactionDto orderToTransaction(OrderOnline o, List<Ticket> tickets, List<OrderDetailFood> foods) {
        // Backfill QR còn thiếu đã được xử lý theo lô ở listTransactions() — không lặp lại ở đây.
        List<MeTransactionItemDto> items = new ArrayList<>();

        for (Ticket t : tickets) {
            Showtime st = t.getShowtime();
            String movieTitle = st != null && st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim";
            String moviePoster = st != null && st.getMovie() != null ? st.getMovie().getPoster() : null;
            String when = formatShowtime(st);
            Seat seat = t.getSeat();
            String seatLabel = t.getSeatLabel() != null ? t.getSeatLabel()
                    : seat != null ? (String.valueOf(seat.getRow()) + String.valueOf(seat.getNumber())) : "";
            String showDate = st != null && st.getStartTime() != null ? st.getStartTime().toLocalDate().toString() : null;
            String showTime = st != null && st.getStartTime() != null ? st.getStartTime().toLocalTime().format(TIME_FMT) : null;
            String roomName = st != null && st.getRoom() != null ? st.getRoom().getName() : null;
            String cinemaName = st != null && st.getRoom() != null && st.getRoom().getCinema() != null
                    ? st.getRoom().getCinema().getName()
                    : null;
            String cinemaAddress = st != null && st.getRoom() != null && st.getRoom().getCinema() != null
                    ? st.getRoom().getCinema().getAddress()
                    : null;
            String qrToken = t.getQrToken();
            double price = t.getPrice() != null ? t.getPrice() : 0;
            items.add(MeTransactionItemDto.builder()
                    .label(movieTitle)
                    .sub((when.isEmpty() ? "" : when + " · ") + "Ghế " + seatLabel)
                    .price(price)
                    .qty(1)
                    .icon("🎫")
                    .ticketId(t.getTicketId())
                    .ticketCode(t.getTicketCode())
                    .qrToken(qrToken)
                    .qrImagePath(qrToken != null && !qrToken.isBlank()
                            ? "/ticket-orders/qr/" + qrToken
                            : null)
                    .moviePoster(moviePoster)
                    .showDate(showDate)
                    .showTime(showTime)
                    .seatLabel(seatLabel)
                    .roomName(roomName)
                    .cinemaName(cinemaName)
                    .cinemaAddress(cinemaAddress)
                    .build());
        }
        for (OrderDetailFood f : foods) {
            Product p = f.getProduct();
            String name = p != null ? p.getName() : "Sản phẩm";
            int q = f.getQuantity() != null ? f.getQuantity() : 1;
            double unit = f.getPrice() != null ? f.getPrice() : 0;
            items.add(MeTransactionItemDto.builder()
                    .label(name)
                    .sub("x" + q)
                    .price(unit * q)
                    .qty(1)
                    .icon("🍿")
                    .build());
        }

        if (items.isEmpty()) {
            items.add(MeTransactionItemDto.builder()
                    .label("Đơn hàng #" + o.getOrderOnlineId())
                    .sub("")
                    .price(o.getFinalAmount() != null ? o.getFinalAmount() : 0)
                    .qty(1)
                    .icon("🧾")
                    .build());
        }

        boolean hasTickets = !tickets.isEmpty();
        String type = hasTickets ? "ticket_online" : "food";
        String stLabel = mapOrderStatus(o.getStatus());

        String voucherCode = null;
        if (o.getUserVoucher() != null && o.getUserVoucher().getVoucher() != null) {
            voucherCode = o.getUserVoucher().getVoucher().getCode();
        }

        double orig = o.getOriginalAmount() != null ? o.getOriginalAmount() : 0;
        double disc = o.getDiscountAmount() != null ? o.getDiscountAmount() : 0;
        double fin = o.getFinalAmount() != null ? o.getFinalAmount() : 0;

        return MeTransactionDto.builder()
                .id(String.valueOf(o.getOrderOnlineId()))
                .orderCode(o.getOrderCode() != null ? o.getOrderCode() : String.valueOf(o.getOrderOnlineId()))
                .type(type)
                .status(stLabel)
                .items(items)
                .originalAmount(orig)
                .discountAmount(disc)
                .finalAmount(fin)
                .createdAt(o.getCreatedAt())
                .pointsEarned(0)
                .voucherCode(voucherCode)
                .build();
    }

    private static String mapOrderStatus(Integer status) {
        if (status == null) {
            return "pending";
        }
        if (status == 0) {
            return "pending";
        }
        if (status == 1) {
            return "completed";
        }
        return "cancelled";
    }

    private MeTransactionDto pointsToTransaction(PointsHistory ph) {
        int pts = ph.getPoints() != null ? ph.getPoints() : 0;
        LocalDateTime at = ph.getDate() != null ? ph.getDate().atStartOfDay() : LocalDateTime.now();
        String desc = ph.getDescription() != null ? ph.getDescription() : "Điểm";
        return MeTransactionDto.builder()
                .id("ph-" + ph.getPointHistoryId())
                .orderCode("PTS-" + ph.getPointHistoryId())
                .type("points")
                .status("completed")
                .items(List.of(MeTransactionItemDto.builder()
                        .label(desc)
                        .sub("")
                        .price(pts)
                        .qty(1)
                        .icon("⭐")
                        .build()))
                .originalAmount(0)
                .discountAmount(0)
                .finalAmount(pts)
                .createdAt(at)
                .pointsEarned(0)
                .voucherCode(null)
                .build();
    }

    private String formatShowtime(Showtime st) {
        if (st == null || st.getStartTime() == null) {
            return "";
        }
        return st.getStartTime().toLocalDate() + " " + st.getStartTime().toLocalTime().format(TIME_FMT);
    }

    public PagedResponse<MeFavoriteMovieDto> listFavorites(Integer userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var favPage = favoriteRepository.findByUser_UserIdOrderByFavoriteIdDesc(userId, PageRequest.of(safePage, safeSize));
        return PagedResponse.of(favPage.map(f -> toFavoriteDto(userId, f)));
    }

    private MeFavoriteMovieDto toFavoriteDto(Integer userId, Favorite f) {
        Movie m = f.getMovie();
        if (m == null) {
            return MeFavoriteMovieDto.builder()
                    .favoriteId(f.getFavoriteId())
                    .movieId(null)
                    .title("—")
                    .poster(null)
                    .duration(null)
                    .status(null)
                    .canReview(false)
                    .review(null)
                    .build();
        }
        MeMovieReviewDto review = findMyReview(userId, m.getMovieId());
        boolean canReview = review != null || ticketRepository.existsPaidTicketByUserIdAndMovieId(userId, m.getMovieId());
        return MeFavoriteMovieDto.builder()
                .favoriteId(f.getFavoriteId())
                .movieId(m.getMovieId())
                .title(m.getTitle())
                .poster(m.getPoster())
                .duration(m.getDuration())
                .status(m.getStatus())
                .canReview(canReview)
                .review(review)
                .build();
    }

    @Transactional
    public void addFavorite(Integer userId, FavoriteMovieIdRequest req) {
        Integer mid = req.movieId();
        if (favoriteRepository.existsByUser_UserIdAndMovie_MovieId(userId, mid)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy user"));
        Movie movie = movieRepository.findById(mid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim"));
        Favorite fav = new Favorite();
        fav.setUser(user);
        fav.setMovie(movie);
        favoriteRepository.save(fav);
    }

    @Transactional
    public void removeFavorite(Integer userId, Integer movieId) {
        favoriteRepository.deleteByUser_UserIdAndMovie_MovieId(userId, movieId);
    }

    @Transactional
    public MeMovieReviewDto saveMovieReview(Integer userId, Integer movieId, MovieReviewRequest req) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim");
        }

        List<Ticket> paidTickets = ticketRepository.findPaidTicketsByUserIdAndMovieId(userId, movieId);
        if (paidTickets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn cần mua vé và thanh toán thành công trước khi đánh giá phim này");
        }

        Review review = reviewRepository.findByUserIdAndMovieId(userId, movieId).stream()
                .findFirst()
                .orElseGet(() -> {
                    Review r = new Review();
                    r.setTicket(paidTickets.get(0));
                    return r;
                });

        review.setRating(req.rating());
        review.setComment(normalizeComment(req.comment()));
        return toReviewDto(reviewRepository.save(review));
    }

    public MeMovieReviewStatusDto getMovieReviewStatus(Integer userId, Integer movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim");
        }
        MeMovieReviewDto review = findMyReview(userId, movieId);
        boolean canReview = review != null || ticketRepository.existsPaidTicketByUserIdAndMovieId(userId, movieId);
        return MeMovieReviewStatusDto.builder()
                .movieId(movieId)
                .canReview(canReview)
                .review(review)
                .build();
    }

    public List<MeMovieReviewDto> listMovieReviews(Integer movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phim");
        }
        return reviewRepository.findPublicByMovieId(movieId).stream()
                .map(this::toReviewDto)
                .collect(Collectors.toList());
    }

    private MeMovieReviewDto findMyReview(Integer userId, Integer movieId) {
        return reviewRepository.findByUserIdAndMovieId(userId, movieId).stream()
                .findFirst()
                .map(this::toReviewDto)
                .orElse(null);
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private MeMovieReviewDto toReviewDto(Review review) {
        if (review == null) {
            return null;
        }
        Ticket ticket = review.getTicket();
        Showtime showtime = ticket != null ? ticket.getShowtime() : null;
        Movie movie = showtime != null ? showtime.getMovie() : null;
        OrderOnline order = ticket != null ? ticket.getOrderOnline() : null;
        User user = order != null ? order.getUser() : null;
        String userName = user != null
                ? (user.getFullname() != null && !user.getFullname().isBlank() ? user.getFullname() : user.getEmail())
                : "Khách hàng";

        return MeMovieReviewDto.builder()
                .reviewId(review.getReviewId())
                .movieId(movie != null ? movie.getMovieId() : null)
                .ticketId(ticket != null ? ticket.getTicketId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .userName(userName)
                .userAvatar(user != null ? user.getAvatar() : null)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    public PagedResponse<MeUserVoucherRowDto> listUserVouchers(Integer userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var uvPage = userVoucherRepository.findByUser_UserIdOrderByUserVoucherIdDesc(userId, PageRequest.of(safePage, safeSize));
        return PagedResponse.of(uvPage.map(uv -> MeUserVoucherRowDto.builder()
                .userVoucherId(uv.getUserVoucherId())
                .status(uv.getStatus() != null ? uv.getStatus() : 1)
                .voucher(toVoucherDto(uv.getVoucher()))
                .build()));
    }

    private VoucherDTO toVoucherDto(Voucher v) {
        if (v == null) {
            return null;
        }
        return VoucherDTO.builder()
                .id(v.getVouchersId())
                .code(v.getCode())
                .discountType(normalizeDiscountType(v.getDiscountType()))
                .value(v.getValue())
                .minOrderValue(v.getMinOrderValue())
                .maxDiscountAmount(v.getMaxDiscountAmount())
                .startDate(v.getStartDate())
                .endDate(v.getEndDate())
                .pointVoucher(java.math.BigDecimal.valueOf(v.getPointVoucher() != null ? v.getPointVoucher() : 0))
                .status(v.getStatus() != null ? v.getStatus() : 1)
                .cinemaId(v.getCinema() != null ? v.getCinema().getCinemaId() : null)
                .cinemaName(v.getCinema() != null ? v.getCinema().getName() : null)
                .build();
    }

    private String normalizeDiscountType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (normalized.equals("FIXED") || normalized.equals("AMOUNT") || normalized.equals("MONEY")) {
            return "FIXED";
        }
        return "PERCENT";
    }

    public PagedResponse<MePointsHistoryDto> listPointsHistory(Integer userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var phPage = pointsHistoryRepository.findByUser_UserIdOrderByDateDescPointHistoryIdDesc(userId, PageRequest.of(safePage, safeSize));
        return PagedResponse.of(phPage.map(ph -> MePointsHistoryDto.builder()
                .pointHistoryId(ph.getPointHistoryId())
                .date(ph.getDate())
                .description(ph.getDescription())
                .points(ph.getPoints())
                .build()));
    }

    @Transactional
    public void redeemVoucher(Integer userId, Integer voucherId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy user"));
        Voucher v = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher"));

        if (v.getStatus() == null || v.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher không khả dụng");
        }
        LocalDate today = LocalDate.now();
        if (v.getStartDate() != null && today.isBefore(v.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher chưa có hiệu lực");
        }
        if (v.getEndDate() != null && today.isAfter(v.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher đã hết hạn");
        }

        int cost = v.getPointVoucher() != null ? v.getPointVoucher() : 0;
        int balance = user.getPoints() != null ? user.getPoints() : 0;
        if (cost > 0 && balance < cost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đủ điểm để đổi voucher");
        }

        if (userVoucherRepository.existsByUser_UserIdAndVoucher_VouchersIdAndStatus(userId, voucherId, 1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã có voucher này trong ví (chưa dùng)");
        }

        if (cost > 0) {
            user.setPoints(balance - cost);
            userRepository.save(user);
        }

        UserVoucher uv = new UserVoucher();
        uv.setUser(user);
        uv.setVoucher(v);
        uv.setStatus(1);
        userVoucherRepository.save(uv);

        if (cost > 0) {
            PointsHistory ph = new PointsHistory();
            ph.setUser(user);
            ph.setDate(today);
            ph.setDescription("Đổi voucher " + (v.getCode() != null ? v.getCode() : ""));
            ph.setPoints(-cost);
            pointsHistoryRepository.save(ph);
        }
    }
}

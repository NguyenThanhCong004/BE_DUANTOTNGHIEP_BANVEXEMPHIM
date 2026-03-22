package com.fpoly.duan.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.VoucherDTO;
import com.fpoly.duan.dto.me.FavoriteMovieIdRequest;
import com.fpoly.duan.dto.me.MeFavoriteMovieDto;
import com.fpoly.duan.dto.me.MePointsHistoryDto;
import com.fpoly.duan.dto.me.MeTransactionDto;
import com.fpoly.duan.dto.me.MeTransactionItemDto;
import com.fpoly.duan.dto.me.MeUserVoucherRowDto;
import com.fpoly.duan.entity.Favorite;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.PointsHistory;
import com.fpoly.duan.entity.Product;
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
    private final UserVoucherRepository userVoucherRepository;
    private final VoucherRepository voucherRepository;
    private final UserRepository userRepository;

    public List<MeTransactionDto> listTransactions(Integer userId) {
        List<MeTransactionDto> out = new ArrayList<>();
        for (OrderOnline o : orderOnlineRepository.findByUser_UserIdOrderByCreatedAtDesc(userId)) {
            out.add(orderToTransaction(o));
        }
        for (PointsHistory ph : pointsHistoryRepository.findByUser_UserIdOrderByDateDescPointHistoryIdDesc(userId)) {
            out.add(pointsToTransaction(ph));
        }
        out.sort(Comparator.comparing(MeTransactionDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return out;
    }

    private MeTransactionDto orderToTransaction(OrderOnline o) {
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());
        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());
        List<MeTransactionItemDto> items = new ArrayList<>();

        for (Ticket t : tickets) {
            Showtime st = t.getShowtime();
            String movieTitle = st != null && st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim";
            String when = formatShowtime(st);
            Seat seat = t.getSeat();
            String seatLabel = seat != null ? (String.valueOf(seat.getRow()) + String.valueOf(seat.getNumber())) : "";
            double price = t.getPrice() != null ? t.getPrice() : 0;
            items.add(MeTransactionItemDto.builder()
                    .label(movieTitle)
                    .sub((when.isEmpty() ? "" : when + " · ") + "Ghế " + seatLabel)
                    .price(price)
                    .qty(1)
                    .icon("🎫")
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

    public List<MeFavoriteMovieDto> listFavorites(Integer userId) {
        return favoriteRepository.findByUser_UserIdOrderByFavoriteIdDesc(userId).stream()
                .map(this::toFavoriteDto)
                .collect(Collectors.toList());
    }

    private MeFavoriteMovieDto toFavoriteDto(Favorite f) {
        Movie m = f.getMovie();
        if (m == null) {
            return MeFavoriteMovieDto.builder()
                    .favoriteId(f.getFavoriteId())
                    .movieId(null)
                    .title("—")
                    .poster(null)
                    .duration(null)
                    .status(null)
                    .build();
        }
        return MeFavoriteMovieDto.builder()
                .favoriteId(f.getFavoriteId())
                .movieId(m.getMovieId())
                .title(m.getTitle())
                .poster(m.getPoster())
                .duration(m.getDuration())
                .status(m.getStatus())
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

    public List<MeUserVoucherRowDto> listUserVouchers(Integer userId) {
        return userVoucherRepository.findByUser_UserIdOrderByUserVoucherIdDesc(userId).stream()
                .map(uv -> MeUserVoucherRowDto.builder()
                        .userVoucherId(uv.getUserVoucherId())
                        .status(uv.getStatus() != null ? uv.getStatus() : 1)
                        .voucher(toVoucherDto(uv.getVoucher()))
                        .build())
                .collect(Collectors.toList());
    }

    private VoucherDTO toVoucherDto(Voucher v) {
        if (v == null) {
            return null;
        }
        return VoucherDTO.builder()
                .id(v.getVouchersId())
                .code(v.getCode())
                .discountType(v.getDiscountType())
                .value(v.getValue())
                .minOrderValue(v.getMinOrderValue())
                .startDate(v.getStartDate())
                .endDate(v.getEndDate())
                .pointVoucher(v.getPointVoucher())
                .status(v.getStatus() != null ? v.getStatus() : 1)
                .build();
    }

    public List<MePointsHistoryDto> listPointsHistory(Integer userId) {
        return pointsHistoryRepository.findByUser_UserIdOrderByDateDescPointHistoryIdDesc(userId).stream()
                .map(ph -> MePointsHistoryDto.builder()
                        .pointHistoryId(ph.getPointHistoryId())
                        .date(ph.getDate())
                        .description(ph.getDescription())
                        .points(ph.getPoints())
                        .build())
                .collect(Collectors.toList());
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
        if (cost <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher này không đổi bằng điểm");
        }
        int balance = user.getPoints() != null ? user.getPoints() : 0;
        if (balance < cost) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đủ điểm để đổi voucher");
        }

        if (userVoucherRepository.existsByUser_UserIdAndVoucher_VouchersIdAndStatus(userId, voucherId, 1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã có voucher này trong ví (chưa dùng)");
        }

        user.setPoints(balance - cost);
        userRepository.save(user);

        UserVoucher uv = new UserVoucher();
        uv.setUser(user);
        uv.setVoucher(v);
        uv.setStatus(1);
        userVoucherRepository.save(uv);

        PointsHistory ph = new PointsHistory();
        ph.setUser(user);
        ph.setDate(today);
        ph.setDescription("Đổi voucher " + (v.getCode() != null ? v.getCode() : ""));
        ph.setPoints(-cost);
        pointsHistoryRepository.save(ph);
    }
}

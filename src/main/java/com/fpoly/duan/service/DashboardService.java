package com.fpoly.duan.service;

import com.fpoly.duan.dto.AuditLogDTO;
import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.CinemaDetailDTO;
import com.fpoly.duan.dto.CinemaCustomerStatDTO;
import com.fpoly.duan.dto.CustomerStatDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.InvoiceStatDTO;
import com.fpoly.duan.dto.ProductStatDTO;
import com.fpoly.duan.dto.StaffStatDTO;
import com.fpoly.duan.dto.CinemaStatDTO;
import com.fpoly.duan.dto.MovieCinemaRevenueDTO;
import com.fpoly.duan.dto.MovieStatDTO;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.dto.SeatOccupancyDTO;
import com.fpoly.duan.dto.TopMovieDTO;
import com.fpoly.duan.entity.AuditLog;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.AuditLogRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.ProductRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final StaffRepository staffRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final AuditLogRepository auditLogRepository;
    private final MembershipRankRepository membershipRankRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final ProductRepository productRepository;

    public DashboardSummaryDTO getSummary() {
        try {
            Double totalRevenue = parseDouble(orderOnlineRepository.sumTotalRevenue());
            Long totalTickets = parseLong(ticketRepository.countAllPaidTickets());

            Long totalUsers = userRepository.count();
            Long totalCinemas = cinemaRepository.count();
            Long totalRooms = roomRepository.count();
            Long totalStaff = staffRepository.countByRoleIgnoreCase("STAFF");
            Long totalAdmins = staffRepository.countByRoleIgnoreCase("ADMIN");
            Long totalMovies = movieRepository.count();

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime firstDayThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime firstDayLastMonth = firstDayThisMonth.minusMonths(1);
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);

            Double thisM = parseDouble(orderOnlineRepository.sumRevenueBetween(firstDayThisMonth, now));
            Double lastM = parseDouble(orderOnlineRepository.sumRevenueBetween(firstDayLastMonth, firstDayThisMonth));
            Double revenueToday = parseDouble(orderOnlineRepository.sumRevenueBetween(todayStart, todayEnd));

            double growth = 0.0;
            if (lastM > 0) {
                growth = ((thisM - lastM) / lastM) * 100.0;
            } else if (thisM > 0) {
                growth = 100.0; // 100% growth if previous month was 0
            }

            return DashboardSummaryDTO.builder()
                    .totalRevenue(totalRevenue)
                    .revenueToday(revenueToday)
                    .totalTicketsSold(totalTickets)
                    .totalUsers(totalUsers)
                    .totalCinemas(totalCinemas)
                    .totalRooms(totalRooms)
                    .totalStaff(totalStaff)
                    .totalAdmins(totalAdmins)
                    .totalMovies(totalMovies)
                    .revenueGrowth(growth)
                    .build();
        } catch (Exception e) {
            log.error("Error generating Dashboard Summary: ", e);
            return DashboardSummaryDTO.builder()
                    .totalRevenue(0.0)
                    .revenueToday(0.0)
                    .totalTicketsSold(0L)
                    .totalUsers(0L)
                    .totalCinemas(0L)
                    .totalRooms(0L)
                    .totalStaff(0L)
                    .totalAdmins(0L)
                    .totalMovies(0L)
                    .revenueGrowth(0.0)
                    .build();
        }
    }

    public List<RevenueChartDTO> getMonthlyRevenue(int year) {
        List<RevenueChartDTO> chartData = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            chartData.add(new RevenueChartDTO("Tháng " + i, 0.0));
        }

        try {
            List<Object[]> results = orderOnlineRepository.getMonthlyRevenueByYear(year);
            if (results != null) {
                for (Object[] result : results) {
                    if (result.length >= 2 && result[0] != null && result[1] != null) {
                        int month = ((Number) result[0]).intValue();
                        double revenue = ((Number) result[1]).doubleValue();
                        if (month >= 1 && month <= 12) {
                            chartData.get(month - 1).setTotalAmount(revenue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error generating Monthly Revenue Chart for year {}: ", year, e);
        }

        return chartData;
    }

    /** Doanh thu theo ngày (30 ngày gần nhất), toàn hệ thống. */
    public List<RevenueChartDTO> getDailyRevenue() {
        try {
            LocalDateTime start = LocalDate.now().minusDays(29).atStartOfDay();
            List<Object[]> rows = orderOnlineRepository.getDailyRevenueSince(start);
            return toRevenueChartList(rows);
        } catch (Exception e) {
            log.error("Error generating Daily Revenue Chart: ", e);
            return new ArrayList<>();
        }
    }

    /** Doanh thu theo ngày trong khoảng [from, to] cho trước (bộ lọc từ ngày - đến ngày), toàn hệ thống. */
    public List<RevenueChartDTO> getDailyRevenueBetween(LocalDate from, LocalDate to) {
        try {
            LocalDateTime start = from.atStartOfDay();
            LocalDateTime end = to.plusDays(1).atStartOfDay();
            List<Object[]> rows = orderOnlineRepository.getDailyRevenueBetween(start, end);
            return toRevenueChartList(rows);
        } catch (Exception e) {
            log.error("Error generating Daily Revenue Chart between {} and {}: ", from, to, e);
            return new ArrayList<>();
        }
    }

    /** Doanh thu theo năm (5 năm gần nhất), toàn hệ thống. */
    public List<RevenueChartDTO> getYearlyRevenue() {
        try {
            int sinceYear = LocalDate.now().getYear() - 4;
            List<Object[]> rows = orderOnlineRepository.getYearlyRevenueSince(sinceYear);
            List<RevenueChartDTO> chartData = new ArrayList<>();
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    chartData.add(new RevenueChartDTO(String.valueOf(((Number) row[0]).intValue()),
                            ((Number) row[1]).doubleValue()));
                }
            }
            return chartData;
        } catch (Exception e) {
            log.error("Error generating Yearly Revenue Chart: ", e);
            return new ArrayList<>();
        }
    }

    public List<CinemaRankingDTO> getCinemaRankings(int year, int month) {
        List<CinemaRankingDTO> ranking = new ArrayList<>();
        try {
            List<Object[]> results = orderOnlineRepository.getCinemaRankings(year, month);
            if (results != null) {
                for (Object[] result : results) {
                    if (result.length >= 3) {
                        String name = result[0] != null ? result[0].toString() : "N/A";
                        Double revenue = result[1] != null ? ((Number) result[1]).doubleValue() : 0.0;
                        Long count = result[2] != null ? ((Number) result[2]).longValue() : 0L;
                        ranking.add(new CinemaRankingDTO(name, revenue, count));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error generating Cinema Rankings for year {} month {}: ", year, month, e);
        }
        return ranking;
    }

    public List<TopMovieDTO> getTopMovies(int limit) {
        try {
            return ticketRepository.getTopMoviesByRevenue().stream()
                    .limit(limit)
                    .map(this::toTopMovieDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Top Movies: ", e);
            return new ArrayList<>();
        }
    }

    public SeatOccupancyDTO getSeatOccupancyToday() {
        try {
            LocalDateTime start = LocalDate.now().atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            long sold = parseLong(ticketRepository.countSoldSeatsForShowtimesBetween(start, end));
            List<Integer> roomIds = showtimeRepository.findDistinctRoomIdsWithShowtimeBetween(start, end);
            long total = roomIds.isEmpty() ? 0L : seatRepository.countByRoom_RoomIdIn(roomIds);
            double ratio = total > 0 ? (sold * 100.0 / total) : 0.0;
            return SeatOccupancyDTO.builder().soldSeats(sold).totalSeats(total).ratio(ratio).build();
        } catch (Exception e) {
            log.error("Error computing Seat Occupancy: ", e);
            return SeatOccupancyDTO.builder().soldSeats(0L).totalSeats(0L).ratio(0.0).build();
        }
    }

    /** PayOS thực chất là một hình thức chuyển khoản — gộp chung vào "TRANSFER" để không tách rời trên biểu đồ. */
    public List<RevenueBreakdownDTO> getPaymentMethodRevenue() {
        try {
            Map<String, Double> totals = new LinkedHashMap<>();
            for (Object[] row : orderOnlineRepository.getRevenueBreakdownAll()) {
                String method = row[0] != null ? row[0].toString() : "N/A";
                Double amount = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
                String bucket = "PAYOS".equalsIgnoreCase(method) ? "TRANSFER" : method;
                totals.merge(bucket, amount, Double::sum);
            }
            return totals.entrySet().stream()
                    .map(e -> RevenueBreakdownDTO.builder().method(e.getKey()).total(e.getValue()).build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Payment Method Revenue: ", e);
            return new ArrayList<>();
        }
    }

    public List<AuditLogDTO> getRecentAdminActivity(int limit) {
        try {
            return auditLogRepository.findByActorRoleIgnoreCaseOrderByCreatedAtDesc("ADMIN", PageRequest.of(0, limit)).stream()
                    .map(this::toAuditLogDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error loading recent admin activity: ", e);
            return new ArrayList<>();
        }
    }

    public List<AuditLogDTO> getAuditLog(int limit) {
        try {
            return auditLogRepository.findTop20ByOrderByCreatedAtDesc().stream()
                    .limit(limit)
                    .map(this::toAuditLogDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error loading audit log: ", e);
            return new ArrayList<>();
        }
    }

    public CinemaDetailDTO getCinemaDetail(Integer cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Không tìm thấy rạp với id: " + cinemaId));

        Map<String, Double> revenueByMovie = new LinkedHashMap<>();
        ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() != null && t.getStatus() == 1)
                .filter(t -> t.getShowtime() != null
                        && t.getShowtime().getRoom() != null
                        && t.getShowtime().getRoom().getCinema() != null
                        && cinemaId.equals(t.getShowtime().getRoom().getCinema().getCinemaId()))
                .forEach(t -> {
                    String title = t.getShowtime().getMovie() != null
                            ? t.getShowtime().getMovie().getTitle()
                            : "Không rõ phim";
                    revenueByMovie.merge(title, t.getPrice() != null ? t.getPrice() : 0.0, Double::sum);
                });

        List<CinemaDetailDTO.MovieRev> topMovies = revenueByMovie.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> new CinemaDetailDTO.MovieRev(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        List<CinemaDetailDTO.Recommendation> recommendations = topMovies.isEmpty()
                ? List.of(new CinemaDetailDTO.Recommendation(
                        "Chưa đủ dữ liệu",
                        "Rạp chưa có doanh thu vé hoàn tất để phân tích.",
                        "Tạo suất chiếu và hoàn tất đơn hàng để có gợi ý."))
                : topMovies.stream()
                        .limit(3)
                        .map(m -> new CinemaDetailDTO.Recommendation(
                                m.getTitle(),
                                "Phim đang đóng góp doanh thu tốt tại rạp.",
                                "Ưu tiên suất chiếu khung giờ cao điểm nếu còn lịch trống."))
                        .collect(Collectors.toList());

        return CinemaDetailDTO.builder()
                .cinemaName(cinema.getName())
                .topMovies(topMovies)
                .recommendations(recommendations)
                .build();
    }

    private List<RevenueChartDTO> toRevenueChartList(List<Object[]> rows) {
        List<RevenueChartDTO> chartData = new ArrayList<>();
        if (rows == null) {
            return chartData;
        }
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] != null) {
                double revenue = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
                chartData.add(new RevenueChartDTO(row[0].toString(), revenue));
            }
        }
        return chartData;
    }

    private TopMovieDTO toTopMovieDTO(Object[] row) {
        String title = row[0] != null ? row[0].toString() : "N/A";
        Long tickets = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
        Double revenue = row[2] instanceof Number ? ((Number) row[2]).doubleValue() : 0.0;
        return TopMovieDTO.builder().movieTitle(title).ticketsSold(tickets).revenue(revenue).build();
    }

    private AuditLogDTO toAuditLogDTO(AuditLog a) {
        return AuditLogDTO.builder()
                .id(a.getId())
                .actorName(a.getActorName())
                .actorRole(a.getActorRole())
                .action(a.getAction())
                .targetType(a.getTargetType())
                .targetId(a.getTargetId())
                .description(a.getDescription())
                .createdAt(a.getCreatedAt())
                .build();
    }

    public List<MovieStatDTO> getMovieStats() {
        try {
            // Build a map of ticket stats for movies that have sales
            Map<Integer, long[]> statsMap = new java.util.HashMap<>();
            for (Object[] row : ticketRepository.getMovieStatsSortedByRevenue()) {
                Integer movieId = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (movieId == null) continue;
                long tickets = row[4] instanceof Number ? ((Number) row[4]).longValue() : 0L;
                long revenueCents = row[5] instanceof Number ? Math.round(((Number) row[5]).doubleValue() * 100) : 0L;
                statsMap.put(movieId, new long[]{tickets, revenueCents});
            }

            // All movies — kể cả phim chưa có vé nào
            return movieRepository.findAll().stream()
                    .map(m -> {
                        long[] s = statsMap.getOrDefault(m.getMovieId(), new long[]{0L, 0L});
                        return MovieStatDTO.builder()
                                .movieId(m.getMovieId())
                                .title(m.getTitle())
                                .poster(m.getPoster())
                                .status(m.getStatus())
                                .ticketsSold(s[0])
                                .revenue(s[1] / 100.0)
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(MovieStatDTO::getRevenue).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Movie Stats: ", e);
            return new ArrayList<>();
        }
    }

    public List<CinemaStatDTO> getCinemaStats() {
        try {
            // Revenue + ticket count per cinema
            Map<Integer, double[]> revenueMap = new java.util.HashMap<>();
            for (Object[] row : ticketRepository.getCinemaRevenueSummary()) {
                Integer cinemaId = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (cinemaId == null) continue;
                double revenue = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
                long tickets  = row[2] instanceof Number ? ((Number) row[2]).longValue()  : 0L;
                revenueMap.put(cinemaId, new double[]{revenue, tickets});
            }

            // Rooms per cinema — GROUP BY query avoids loading full entities
            Map<Integer, Long> roomMap = new java.util.HashMap<>();
            for (Object[] row : roomRepository.countRoomsGroupByCinema()) {
                Integer cid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (cid != null)
                    roomMap.put(cid, row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L);
            }

            // Staff per cinema — GROUP BY query avoids loading full entities
            Map<Integer, Long> staffMap = new java.util.HashMap<>();
            for (Object[] row : staffRepository.countStaffGroupByCinema()) {
                Integer cid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (cid != null)
                    staffMap.put(cid, row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L);
            }

            return cinemaRepository.findAll().stream()
                    .map(c -> {
                        double[] rev = revenueMap.getOrDefault(c.getCinemaId(), new double[]{0.0, 0.0});
                        return CinemaStatDTO.builder()
                                .cinemaId(c.getCinemaId())
                                .name(c.getName())
                                .address(c.getAddress())
                                .status(c.getStatus())
                                .totalRooms(roomMap.getOrDefault(c.getCinemaId(), 0L))
                                .totalStaff(staffMap.getOrDefault(c.getCinemaId(), 0L))
                                .revenue(rev[0])
                                .ticketCount((long) rev[1])
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(CinemaStatDTO::getRevenue).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Cinema Stats: ", e);
            return new ArrayList<>();
        }
    }

    public List<CustomerStatDTO> getCustomerStats(Integer cinemaId) {
        try {
            // Đếm đơn hàng hoàn tất mỗi user
            Map<Integer, Long> orderCountMap = new java.util.HashMap<>();
            for (Object[] row : orderOnlineRepository.countCompletedOrdersPerUser()) {
                Integer uid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (uid == null) continue;
                orderCountMap.put(uid, row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L);
            }

            // Tổng chi tiêu toàn thời gian mỗi user (hiển thị)
            Map<Integer, Double> allTimeSpendMap = new java.util.HashMap<>();
            for (Object[] row : orderOnlineRepository.sumAllTimeSpendingPerUser()) {
                Integer uid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (uid == null) continue;
                allTimeSpendMap.put(uid, row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0);
            }

            // Chi tiêu năm hiện tại mỗi user (để tính hạng — khớp business logic)
            int currentYear = java.time.LocalDate.now().getYear();
            Map<Integer, Double> yearSpendMap = new java.util.HashMap<>();
            for (Object[] row : orderOnlineRepository.sumCurrentYearSpendingPerUser(currentYear)) {
                Integer uid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (uid == null) continue;
                yearSpendMap.put(uid, row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0);
            }

            // Nếu lọc theo rạp, chỉ lấy user đã đặt tại rạp đó
            java.util.Set<Integer> cinemaUserIds = null;
            if (cinemaId != null) {
                cinemaUserIds = new java.util.HashSet<>(orderOnlineRepository.getUserIdsByCinema(cinemaId));
            }
            final java.util.Set<Integer> cinemaFilter = cinemaUserIds;

            // Sắp xếp hạng theo minSpending giảm dần để dùng cho tìm hạng phù hợp
            List<com.fpoly.duan.entity.MembershipRank> activeRanks = membershipRankRepository.findAll().stream()
                    .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                    .sorted(Comparator.comparingDouble(
                            r -> -(r.getMinSpending() != null ? r.getMinSpending() : 0.0)))
                    .collect(Collectors.toList());

            return userRepository.findAll().stream()
                    .filter(u -> cinemaFilter == null || cinemaFilter.contains(u.getUserId()))
                    .map(u -> {
                        double yearSpend = yearSpendMap.getOrDefault(u.getUserId(), 0.0);
                        // Tìm hạng cao nhất mà yearSpend đạt được
                        String rankName = activeRanks.stream()
                                .filter(r -> yearSpend >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                                .findFirst()
                                .map(com.fpoly.duan.entity.MembershipRank::getRankName)
                                .orElse(activeRanks.isEmpty() ? "Chưa xác định"
                                        : activeRanks.get(activeRanks.size() - 1).getRankName());
                        return CustomerStatDTO.builder()
                                .userId(u.getUserId())
                                .fullName(u.getFullname())
                                .email(u.getEmail())
                                .phone(u.getPhone())
                                .membershipRank(rankName)
                                .totalOrders(orderCountMap.getOrDefault(u.getUserId(), 0L))
                                .totalSpending(allTimeSpendMap.getOrDefault(u.getUserId(), 0.0))
                                .points(u.getPoints() != null ? u.getPoints() : 0)
                                .status(u.getStatus())
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(CustomerStatDTO::getTotalSpending).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Customer Stats: ", e);
            return new ArrayList<>();
        }
    }

    public List<CinemaCustomerStatDTO> getCustomerStatsByCinema() {
        try {
            // Build stats map từ orders (chỉ rạp có đơn)
            Map<Integer, CinemaCustomerStatDTO> statsMap = new java.util.HashMap<>();
            for (Object[] row : orderOnlineRepository.getCustomerStatsByCinema()) {
                Integer cid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (cid == null) continue;
                statsMap.put(cid, CinemaCustomerStatDTO.builder()
                    .cinemaId(cid)
                    .cinemaName(row[1] != null ? row[1].toString() : "—")
                    .uniqueCustomers(row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L)
                    .totalOrders(row[3] instanceof Number ? ((Number) row[3]).longValue() : 0L)
                    .totalRevenue(row[4] instanceof Number ? ((Number) row[4]).doubleValue() : 0.0)
                    .build());
            }
            // Lấy tất cả rạp, fill 0 cho rạp chưa có khách
            return cinemaRepository.findAll().stream()
                .map(c -> statsMap.getOrDefault(c.getCinemaId(), CinemaCustomerStatDTO.builder()
                    .cinemaId(c.getCinemaId())
                    .cinemaName(c.getName())
                    .uniqueCustomers(0L)
                    .totalOrders(0L)
                    .totalRevenue(0.0)
                    .build()))
                .sorted(Comparator.comparingDouble(CinemaCustomerStatDTO::getTotalRevenue).reversed())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Cinema Customer Stats: ", e);
            return new ArrayList<>();
        }
    }

    public List<TopMovieDTO> getCinemaMovieRevenue(Integer cinemaId) {
        try {
            return ticketRepository.getTopMoviesByRevenueForCinema(cinemaId).stream()
                .map(this::toTopMovieDTO)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Cinema Movie Revenue: ", e);
            return new ArrayList<>();
        }
    }

    public List<MovieCinemaRevenueDTO> getMovieCinemaRevenue(Integer movieId) {
        try {
            return ticketRepository.getCinemaRevenueByMovie(movieId).stream()
                .map(row -> MovieCinemaRevenueDTO.builder()
                    .cinemaId(row[0] instanceof Number ? ((Number) row[0]).intValue() : null)
                    .cinemaName(row[1] != null ? row[1].toString() : "—")
                    .ticketsSold(row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L)
                    .revenue(row[3] instanceof Number ? ((Number) row[3]).doubleValue() : 0.0)
                    .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Movie Cinema Revenue: ", e);
            return new ArrayList<>();
        }
    }

    public List<ProductStatDTO> getProductStats() {
        try {
            // Số lượng + doanh thu theo sản phẩm từ đơn hoàn tất
            Map<Integer, long[]> salesMap = new java.util.HashMap<>();
            for (Object[] row : orderDetailFoodRepository.getProductSalesStats()) {
                Integer pid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (pid == null) continue;
                long qty     = row[1] instanceof Number ? ((Number) row[1]).longValue()  : 0L;
                long revCents = row[2] instanceof Number ? Math.round(((Number) row[2]).doubleValue() * 100) : 0L;
                salesMap.put(pid, new long[]{qty, revCents});
            }

            return productRepository.findAll().stream()
                    .map(p -> {
                        long[] s = salesMap.getOrDefault(p.getProductId(), new long[]{0L, 0L});
                        return ProductStatDTO.builder()
                                .productId(p.getProductId())
                                .productName(p.getName())
                                .categoryName(p.getCategory() != null ? p.getCategory().getName() : "—")
                                .unitPrice(p.getPrice() != null ? p.getPrice() : 0.0)
                                .quantitySold(s[0])
                                .totalRevenue(s[1] / 100.0)
                                .status(p.getStatus())
                                .build();
                    })
                    .sorted(Comparator.comparingLong(ProductStatDTO::getQuantitySold).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Product Stats: ", e);
            return new ArrayList<>();
        }
    }

    public Map<String, Double> getCategoryRevenueByMonth(int year, int month) {
        try {
            Map<String, Double> result = new LinkedHashMap<>();
            for (Object[] row : orderDetailFoodRepository.getCategoryRevenueByMonth(year, month)) {
                String cat = row[0] != null ? row[0].toString() : "—";
                double rev = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
                result.merge(cat, rev, Double::sum);
            }
            return result;
        } catch (Exception e) {
            log.error("Error generating Category Revenue by Month: ", e);
            return new LinkedHashMap<>();
        }
    }

    public List<InvoiceStatDTO> getInvoiceStats() {
        try {
            List<OrderOnline> orders = orderOnlineRepository.findAll();

            // Load ticket discounts in bulk — tránh N+1
            List<Integer> orderIds = orders.stream()
                .map(OrderOnline::getOrderOnlineId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

            // ticketDiscount = originalPrice - price (bao gồm giảm khuyến mãi + hạng hội viên)
            Map<Integer, Double> ticketDiscountByOrderId = new HashMap<>();
            if (!orderIds.isEmpty()) {
                ticketRepository.findByOrderOnline_OrderOnlineIdInWithDetails(orderIds)
                    .forEach(t -> {
                        if (t.getOrderOnline() == null) return;
                        int oid = t.getOrderOnline().getOrderOnlineId();
                        double orig  = t.getOriginalPrice() != null ? t.getOriginalPrice() : 0.0;
                        double price = t.getPrice()         != null ? t.getPrice()         : 0.0;
                        double disc  = Math.max(0.0, orig - price);
                        ticketDiscountByOrderId.merge(oid, disc, Double::sum);
                    });
            }

            return orders.stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(o -> {
                    String customerName = "Khách vãng lai";
                    if (o.getUser() != null) {
                        String fn = o.getUser().getFullname();
                        customerName = (fn != null && !fn.isBlank()) ? fn : o.getUser().getEmail();
                    }
                    Integer cinemaId = null;
                    String cinemaName = "—";
                    if (o.getCinema() != null) {
                        cinemaId = o.getCinema().getCinemaId();
                        cinemaName = o.getCinema().getName();
                    } else if (o.getStaff() != null && o.getStaff().getCinema() != null) {
                        cinemaId = o.getStaff().getCinema().getCinemaId();
                        cinemaName = o.getStaff().getCinema().getName();
                    }
                    // Tổng giảm giá = giảm vé (khuyến mãi + hội viên) + giảm voucher
                    double ticketDisc  = ticketDiscountByOrderId.getOrDefault(o.getOrderOnlineId(), 0.0);
                    double voucherDisc = o.getDiscountAmount() != null ? o.getDiscountAmount() : 0.0;
                    double totalDiscount = (double) Math.round(ticketDisc + voucherDisc);

                    return InvoiceStatDTO.builder()
                        .orderId(o.getOrderOnlineId())
                        .orderCode(o.getOrderCode())
                        .customerName(customerName)
                        .cinemaId(cinemaId)
                        .cinemaName(cinemaName)
                        .isCounter(o.getStaff() != null)
                        .paymentMethod(o.getPaymentMethod())
                        .originalAmount(o.getOriginalAmount())
                        .discountAmount(totalDiscount)
                        .finalAmount(o.getFinalAmount())
                        .status(o.getStatus())
                        .createdAt(o.getCreatedAt())
                        .build();
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Invoice Stats: ", e);
            return new ArrayList<>();
        }
    }

    public List<StaffStatDTO> getStaffStats() {
        try {
            // Số đơn + doanh thu mỗi staff
            Map<Integer, long[]> orderMap = new java.util.HashMap<>();
            for (Object[] row : orderOnlineRepository.getStaffOrderStats()) {
                Integer sid = row[0] instanceof Number ? ((Number) row[0]).intValue() : null;
                if (sid == null) continue;
                long orders  = row[1] instanceof Number ? ((Number) row[1]).longValue()  : 0L;
                long revCents = row[2] instanceof Number ? Math.round(((Number) row[2]).doubleValue() * 100) : 0L;
                orderMap.put(sid, new long[]{orders, revCents});
            }

            return staffRepository.findAllExceptSuperAdmin().stream()
                    .map(s -> {
                        long[] stats = orderMap.getOrDefault(s.getStaffId(), new long[]{0L, 0L});
                        return StaffStatDTO.builder()
                                .staffId(s.getStaffId())
                                .fullName(s.getFullname())
                                .email(s.getEmail())
                                .phone(s.getPhone())
                                .role(s.getRole())
                                .cinemaName(s.getCinema() != null ? s.getCinema().getName() : "—")
                                .totalOrders(stats[0])
                                .totalRevenue(stats[1] / 100.0)
                                .status(s.getStatus())
                                .build();
                    })
                    .sorted(Comparator.comparingDouble(StaffStatDTO::getTotalRevenue).reversed())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Staff Stats: ", e);
            return new ArrayList<>();
        }
    }

    // --- Helper Methods for Safe Parsing ---
    private Double parseDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (Exception e) {
            return 0L;
        }
    }
}

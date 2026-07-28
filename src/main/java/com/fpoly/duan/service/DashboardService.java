package com.fpoly.duan.service;

import com.fpoly.duan.dto.AuditLogDTO;
import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.CinemaDetailDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.dto.SeatOccupancyDTO;
import com.fpoly.duan.dto.TopMovieDTO;
import com.fpoly.duan.entity.AuditLog;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.repository.AuditLogRepository;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.MovieRepository;
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

package com.fpoly.duan.service;

import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.CinemaDetailDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    public DashboardSummaryDTO getSummary() {
        try {
            Double totalRevenue = parseDouble(orderOnlineRepository.sumTotalRevenue());
            Long totalTickets = parseLong(ticketRepository.countAllPaidTickets());
            
            Long totalUsers = userRepository.count();
            Long totalCinemas = cinemaRepository.count();
            Long totalStaff = staffRepository.count();
            Long totalMovies = movieRepository.count();

            // Calculate growth (Month-over-Month)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime firstDayThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime firstDayLastMonth = firstDayThisMonth.minusMonths(1);
            
            Double thisM = parseDouble(orderOnlineRepository.sumRevenueBetween(firstDayThisMonth, now));
            Double lastM = parseDouble(orderOnlineRepository.sumRevenueBetween(firstDayLastMonth, firstDayThisMonth));

            double growth = 0.0;
            if (lastM > 0) {
                growth = ((thisM - lastM) / lastM) * 100.0;
            } else if (thisM > 0) {
                growth = 100.0; // 100% growth if previous month was 0
            }

            return DashboardSummaryDTO.builder()
                    .totalRevenue(totalRevenue)
                    .totalTicketsSold(totalTickets)
                    .totalUsers(totalUsers)
                    .totalCinemas(totalCinemas)
                    .totalStaff(totalStaff)
                    .totalMovies(totalMovies)
                    .revenueGrowth(growth)
                    .build();
        } catch (Exception e) {
            log.error("Error generating Dashboard Summary: ", e);
            return DashboardSummaryDTO.builder()
                    .totalRevenue(0.0)
                    .totalTicketsSold(0L)
                    .totalUsers(0L)
                    .totalCinemas(0L)
                    .totalStaff(0L)
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

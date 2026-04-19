package com.fpoly.duan.service;

import com.fpoly.duan.dto.CinemaRankingDTO;
import com.fpoly.duan.dto.DashboardSummaryDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
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
import java.util.List;

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

    public List<CinemaRankingDTO> getCinemaRankings() {
        List<CinemaRankingDTO> ranking = new ArrayList<>();
        try {
            List<Object[]> results = orderOnlineRepository.getCinemaRankings();
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
            log.error("Error generating Cinema Rankings: ", e);
        }
        return ranking;
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

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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CinemaRepository cinemaRepository;
    private final StaffRepository staffRepository;
    private final MovieRepository movieRepository;

    public DashboardSummaryDTO getSummary() {
        Object revObj = orderOnlineRepository.sumTotalRevenue();
        Object tickObj = ticketRepository.countAllPaidTickets();
        
        Double totalRevenue = revObj != null ? Double.parseDouble(revObj.toString()) : 0.0;
        Long totalTickets = tickObj != null ? Long.parseLong(tickObj.toString()) : 0L;
        
        Long totalUsers = userRepository.count();
        Long totalCinemas = cinemaRepository.count();
        Long totalStaff = staffRepository.count();
        Long totalMovies = movieRepository.count();

        // Calculate growth
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstDayThisMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime firstDayLastMonth = firstDayThisMonth.minusMonths(1);
        
        Object thisMObj = orderOnlineRepository.sumRevenueBetween(firstDayThisMonth, now);
        Object lastMObj = orderOnlineRepository.sumRevenueBetween(firstDayLastMonth, firstDayThisMonth);

        double growth = 0.0;
        double thisM = thisMObj != null ? Double.parseDouble(thisMObj.toString()) : 0.0;
        double lastM = lastMObj != null ? Double.parseDouble(lastMObj.toString()) : 0.0;

        if (lastM > 0) {
            growth = (thisM - lastM) / lastM * 100;
        } else if (thisM > 0) {
            growth = 100.0;
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
    }

    public List<RevenueChartDTO> getMonthlyRevenue(int year) {
        List<Object[]> results = orderOnlineRepository.getMonthlyRevenueByYear(year);
        List<RevenueChartDTO> chartData = new ArrayList<>();
        
        for (int i = 1; i <= 12; i++) {
            chartData.add(new RevenueChartDTO("Tháng " + i, 0.0));
        }

        if (results != null) {
            for (Object[] result : results) {
                if (result[0] != null && result[1] != null) {
                    try {
                        int month = Integer.parseInt(result[0].toString());
                        double revenue = Double.parseDouble(result[1].toString());
                        if (month >= 1 && month <= 12) {
                            chartData.get(month - 1).setTotalAmount(revenue);
                        }
                    } catch (Exception e) {
                        // Bỏ qua bản ghi lỗi format
                    }
                }
            }
        }

        return chartData;
    }

    public List<CinemaRankingDTO> getCinemaRankings() {
        List<Object[]> results = orderOnlineRepository.getCinemaRankings();
        List<CinemaRankingDTO> ranking = new ArrayList<>();
        if (results != null) {
            for (Object[] result : results) {
                try {
                    ranking.add(new CinemaRankingDTO(
                            result[0] != null ? result[0].toString() : "N/A",
                            result[1] != null ? Double.parseDouble(result[1].toString()) : 0.0,
                            result[2] != null ? Long.parseLong(result[2].toString()) : 0L
                    ));
                } catch (Exception e) {
                    // Bỏ qua bản ghi lỗi format
                }
            }
        }
        return ranking;
    }
}

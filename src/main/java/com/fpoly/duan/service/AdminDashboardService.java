package com.fpoly.duan.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fpoly.duan.dto.AdminDashboardSummaryDTO;
import com.fpoly.duan.dto.HourlyTicketsDTO;
import com.fpoly.duan.dto.RevenueChartDTO;
import com.fpoly.duan.dto.SeatTypeRatioDTO;
import com.fpoly.duan.dto.TopMovieDTO;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Dashboard cho Admin — mọi truy vấn đều giới hạn theo một rạp cụ thể (đã resolve qua CinemaScopeService). */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final ShowtimeRepository showtimeRepository;

    public AdminDashboardSummaryDTO getSummary(Integer cinemaId) {
        try {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            LocalDateTime todayEnd = todayStart.plusDays(1);

            Double revenueToday = parseDouble(
                    orderOnlineRepository.sumRevenueByCinemaBetween(cinemaId, todayStart, todayEnd));
            Long ticketsToday = parseLong(
                    ticketRepository.countTicketsSoldByCinemaBetween(cinemaId, todayStart, todayEnd));
            Long customersToday = parseLong(
                    orderOnlineRepository.countDistinctCustomersByCinemaBetween(cinemaId, todayStart, todayEnd));
            Long moviesShowingCount = parseLong(
                    showtimeRepository.countDistinctMoviesShowingBetween(cinemaId, todayStart, todayEnd));
            Long showtimesToday = parseLong(
                    showtimeRepository.countShowtimesByCinemaBetween(cinemaId, todayStart, todayEnd));
            Long seatsSoldToday = parseLong(
                    ticketRepository.countSoldSeatsForCinemaShowtimesBetween(cinemaId, todayStart, todayEnd));

            return AdminDashboardSummaryDTO.builder()
                    .revenueToday(revenueToday)
                    .ticketsToday(ticketsToday)
                    .customersToday(customersToday)
                    .moviesShowingCount(moviesShowingCount)
                    .showtimesToday(showtimesToday)
                    .seatsSoldToday(seatsSoldToday)
                    .build();
        } catch (Exception e) {
            log.error("Error generating Admin Dashboard Summary for cinema {}: ", cinemaId, e);
            return AdminDashboardSummaryDTO.builder()
                    .revenueToday(0.0).ticketsToday(0L).customersToday(0L)
                    .moviesShowingCount(0L).showtimesToday(0L).seatsSoldToday(0L)
                    .build();
        }
    }

    public List<RevenueChartDTO> getRevenueByDay(Integer cinemaId, int days) {
        try {
            LocalDateTime start = LocalDate.now().minusDays(Math.max(days, 1) - 1L).atStartOfDay();
            List<Object[]> rows = orderOnlineRepository.getDailyRevenueByCinemaSince(cinemaId, start);
            List<RevenueChartDTO> chartData = new ArrayList<>();
            for (Object[] row : rows) {
                if (row.length >= 2 && row[0] != null) {
                    double revenue = row[1] instanceof Number ? ((Number) row[1]).doubleValue() : 0.0;
                    chartData.add(new RevenueChartDTO(row[0].toString(), revenue));
                }
            }
            return chartData;
        } catch (Exception e) {
            log.error("Error generating Admin Revenue By Day for cinema {}: ", cinemaId, e);
            return new ArrayList<>();
        }
    }

    public List<HourlyTicketsDTO> getTicketsByHour(Integer cinemaId, LocalDate date) {
        try {
            LocalDate target = date != null ? date : LocalDate.now();
            LocalDateTime start = target.atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            List<HourlyTicketsDTO> result = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                result.add(HourlyTicketsDTO.builder().hour(h).ticketCount(0L).build());
            }
            for (Object[] row : ticketRepository.getTicketsByHourForCinema(cinemaId, start, end)) {
                if (row.length >= 2 && row[0] != null) {
                    int hour = ((Number) row[0]).intValue();
                    long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                    if (hour >= 0 && hour < 24) {
                        result.get(hour).setTicketCount(count);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Error generating Tickets By Hour for cinema {}: ", cinemaId, e);
            return new ArrayList<>();
        }
    }

    public List<TopMovieDTO> getTopMovies(Integer cinemaId, int limit) {
        try {
            return ticketRepository.getTopMoviesByRevenueForCinema(cinemaId).stream()
                    .limit(limit)
                    .map(row -> TopMovieDTO.builder()
                            .movieTitle(row[0] != null ? row[0].toString() : "N/A")
                            .ticketsSold(row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L)
                            .revenue(row[2] instanceof Number ? ((Number) row[2]).doubleValue() : 0.0)
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error generating Top Movies for cinema {}: ", cinemaId, e);
            return new ArrayList<>();
        }
    }

    public List<SeatTypeRatioDTO> getSeatTypeRatioToday(Integer cinemaId) {
        try {
            LocalDateTime start = LocalDate.now().atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            List<SeatTypeRatioDTO> list = new ArrayList<>();
            for (Object[] row : ticketRepository.getSeatTypeRatioForCinemaBetween(cinemaId, start, end)) {
                String name = row[0] != null ? row[0].toString() : "N/A";
                Boolean couple = row[1] instanceof Boolean ? (Boolean) row[1] : Boolean.FALSE;
                Long tickets = row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L;
                list.add(SeatTypeRatioDTO.builder().seatTypeName(name).coupleSeat(couple).ticketsSold(tickets).build());
            }
            return list;
        } catch (Exception e) {
            log.error("Error generating Seat Type Ratio for cinema {}: ", cinemaId, e);
            return new ArrayList<>();
        }
    }

    private Double parseDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return 0.0;
    }

    private Long parseLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return 0L;
    }
}

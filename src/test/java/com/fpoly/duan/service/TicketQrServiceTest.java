package com.fpoly.duan.service;

import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketQrServiceTest {
    private final TicketQrService service = new TicketQrService("test-secret-must-not-be-used-in-production");

    @Test
    void createsEncryptedQrAndCodeDerivedFromCinemaMovieAndShowtime() {
        Ticket first = ticket(101, "Rạp A", "1 Trần Hưng Đạo", "Phim A", LocalDateTime.of(2026, 7, 1, 19, 30));
        Ticket changedShowtime = ticket(101, "Rạp A", "1 Trần Hưng Đạo", "Phim A", LocalDateTime.of(2026, 7, 1, 21, 30));

        service.assignSecureCodes(List.of(first));
        service.assignSecureCodes(List.of(changedShowtime));

        assertTrue(first.getTicketCode().startsWith("TKT-"));
        assertTrue(first.getQrToken().length() > 40);
        assertNotEquals(first.getTicketCode(), changedShowtime.getTicketCode());
        TicketQrService.TicketReference decoded = service.decodeReference(first.getQrToken());
        assertEquals(first.getTicketId(), decoded.ticketId());
        assertEquals(first.getTicketCode(), decoded.ticketCode());
        assertTrue(service.matchesExpectedCode(first));
    }

    @Test
    void createsShortOpaqueReceiptBarcodeToken() {
        Cinema cinema = new Cinema();
        cinema.setCinemaId(4);
        cinema.setName("Chi nhánh Quận 1");
        cinema.setAddress("1 Nguyễn Huệ, Quận 1");
        OrderOnline order = new OrderOnline();
        order.setOrderOnlineId(88);
        order.setOrderCode("POS-123456789");
        order.setCinema(cinema);
        order.setFinalAmount(125000.0);
        order.setCreatedAt(LocalDateTime.of(2026, 7, 1, 19, 30));

        service.assignReceiptToken(order);

        assertTrue(order.getReceiptToken().startsWith("RCP-"));
        assertTrue(!order.getReceiptToken().contains("POS-123456789"));
        assertTrue(!order.getReceiptToken().contains("Quận 1"));
        assertTrue(service.toBarcodePng(order.getReceiptToken()).length > 100);
    }

    private static Ticket ticket(int id, String cinemaName, String address, String movieTitle, LocalDateTime startsAt) {
        Cinema cinema = new Cinema();
        cinema.setName(cinemaName);
        cinema.setAddress(address);
        Room room = new Room();
        room.setCinema(cinema);
        Movie movie = new Movie();
        movie.setTitle(movieTitle);
        Showtime showtime = new Showtime();
        showtime.setRoom(room);
        showtime.setMovie(movie);
        showtime.setStartTime(startsAt);
        Seat seat = new Seat();
        seat.setRow("D");
        seat.setNumber("08");
        Ticket ticket = new Ticket();
        ticket.setTicketId(id);
        ticket.setShowtime(showtime);
        ticket.setSeat(seat);
        return ticket;
    }
}

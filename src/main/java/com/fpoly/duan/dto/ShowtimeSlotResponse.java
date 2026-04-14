package com.fpoly.duan.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO suất chiếu cho FE. JSON giữ snake_case (movie_id, …) cho khớp FE hiện tại.
 */
@Data
@NoArgsConstructor
public class ShowtimeSlotResponse {

    private Integer id;
    private String date;
    private String time;
    private String endTime;

    @JsonProperty("movie_id")
    private Integer movieId;

    @JsonProperty("movie_title")
    private String movieTitle;

    @JsonProperty("room_id")
    private Integer roomId;

    @JsonProperty("room_name")
    private String roomName;

    @JsonProperty("cinema_id")
    private Integer cinemaId;

    @JsonProperty("cinema_name")
    private String cinemaName;

    private Double surcharge;

    @JsonProperty("base_price")
    private Double basePrice;

    private Double price;
    private String status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<Integer> bookedSeatIds;
}

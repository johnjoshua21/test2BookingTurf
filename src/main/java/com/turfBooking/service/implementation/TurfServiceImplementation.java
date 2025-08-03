package com.turfBooking.service.implementation;

import com.turfBooking.dto.TurfRequestDTO;
import com.turfBooking.dto.TurfResponseDTO;
import com.turfBooking.dto.TurfUpdateDTO;
import com.turfBooking.dto.TurfSearchDTO;
import com.turfBooking.entity.Turf;
import com.turfBooking.entity.User;
import com.turfBooking.entity.Booking;
import com.turfBooking.entity.BlockedSlot;
import com.turfBooking.enums.SportType;
import com.turfBooking.repository.TurfRepository;
import com.turfBooking.repository.UserRepository;
import com.turfBooking.service.interfaces.TurfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TurfServiceImplementation implements TurfService {

    @Autowired
    private TurfRepository turfRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public TurfResponseDTO createTurf(TurfRequestDTO turfRequestDTO) {
        // Validate owner exists
        User owner = userRepository.findById(turfRequestDTO.getOwnerId())
                .orElseThrow(() -> new RuntimeException("Owner not found with id: " + turfRequestDTO.getOwnerId()));

        // Validate operating hours
        if (!validateOperatingHours(turfRequestDTO.getOperatingStartTime(), turfRequestDTO.getOperatingEndTime())) {
            throw new RuntimeException("Invalid operating hours: start time must be before end time");
        }

        // Check if turf name already exists for this owner
        if (turfNameExistsForOwner(turfRequestDTO.getName(), turfRequestDTO.getOwnerId())) {
            throw new RuntimeException("Turf name already exists for this owner");
        }

        // Create new turf entity
        Turf turf = new Turf();
        turf.setName(turfRequestDTO.getName());
        turf.setPhone(turfRequestDTO.getPhone());
        turf.setLocation(turfRequestDTO.getLocation());
        turf.setType(turfRequestDTO.getType());
        turf.setPricePerSlot(turfRequestDTO.getPricePerSlot());
        turf.setDescription(turfRequestDTO.getDescription());
        turf.setOperatingStartTime(turfRequestDTO.getOperatingStartTime());
        turf.setOperatingEndTime(turfRequestDTO.getOperatingEndTime());
        turf.setOwner(owner);

        // Save turf
        Turf savedTurf = turfRepository.save(turf);

        return convertToResponseDTO(savedTurf);
    }

    @Override
    @Transactional(readOnly = true)
    public TurfResponseDTO getTurfById(Long id) {
        Turf turf = turfRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turf not found with id: " + id));

        return convertToDetailedResponseDTO(turf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getAllTurfs() {
        return turfRepository.findAll()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TurfResponseDTO updateTurf(Long id, TurfUpdateDTO turfUpdateDTO) {
        Turf turf = turfRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Turf not found with id: " + id));

        // Update only non-null fields
        if (turfUpdateDTO.getName() != null && !turfUpdateDTO.getName().trim().isEmpty()) {
            // Check if new name already exists for this owner (excluding current turf)
            if (!turf.getName().equals(turfUpdateDTO.getName()) &&
                    turfNameExistsForOwner(turfUpdateDTO.getName(), turf.getOwner().getId())) {
                throw new RuntimeException("Turf name already exists for this owner");
            }
            turf.setName(turfUpdateDTO.getName());
        }

        if (turfUpdateDTO.getPhone() != null && !turfUpdateDTO.getPhone().trim().isEmpty()) {
            turf.setPhone(turfUpdateDTO.getPhone());
        }

        if (turfUpdateDTO.getLocation() != null && !turfUpdateDTO.getLocation().trim().isEmpty()) {
            turf.setLocation(turfUpdateDTO.getLocation());
        }

        if (turfUpdateDTO.getType() != null) {
            turf.setType(turfUpdateDTO.getType());
        }

        if (turfUpdateDTO.getPricePerSlot() != null) {
            turf.setPricePerSlot(turfUpdateDTO.getPricePerSlot());
        }

        if (turfUpdateDTO.getDescription() != null) {
            turf.setDescription(turfUpdateDTO.getDescription());
        }

        if (turfUpdateDTO.getOperatingStartTime() != null && turfUpdateDTO.getOperatingEndTime() != null) {
            if (!validateOperatingHours(turfUpdateDTO.getOperatingStartTime(), turfUpdateDTO.getOperatingEndTime())) {
                throw new RuntimeException("Invalid operating hours: start time must be before end time");
            }
            turf.setOperatingStartTime(turfUpdateDTO.getOperatingStartTime());
            turf.setOperatingEndTime(turfUpdateDTO.getOperatingEndTime());
        } else if (turfUpdateDTO.getOperatingStartTime() != null) {
            if (!validateOperatingHours(turfUpdateDTO.getOperatingStartTime(), turf.getOperatingEndTime())) {
                throw new RuntimeException("Invalid operating hours: start time must be before end time");
            }
            turf.setOperatingStartTime(turfUpdateDTO.getOperatingStartTime());
        } else if (turfUpdateDTO.getOperatingEndTime() != null) {
            if (!validateOperatingHours(turf.getOperatingStartTime(), turfUpdateDTO.getOperatingEndTime())) {
                throw new RuntimeException("Invalid operating hours: start time must be before end time");
            }
            turf.setOperatingEndTime(turfUpdateDTO.getOperatingEndTime());
        }

        Turf updatedTurf = turfRepository.save(turf);
        return convertToDetailedResponseDTO(updatedTurf);
    }

    @Override
    public void deleteTurf(Long id) {
        if (!turfRepository.existsById(id)) {
            throw new RuntimeException("Turf not found with id: " + id);
        }
        turfRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getTurfsByOwnerId(Long ownerId) {
        return turfRepository.findByOwnerId(ownerId)
                .stream()
                .map(this::convertToDetailedResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getTurfsBySportType(SportType type) {
        return turfRepository.findByType(type)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> searchTurfsByLocation(String location) {
        return turfRepository.findByLocationContainingIgnoreCase(location)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> searchTurfsByName(String name) {
        return turfRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getTurfsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return turfRepository.findByPricePerSlotBetween(minPrice, maxPrice)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> searchTurfs(TurfSearchDTO searchDTO) {
        return turfRepository.searchTurfs(
                        searchDTO.getName(),
                        searchDTO.getLocation(),
                        searchDTO.getType(),
                        searchDTO.getMinPrice(),
                        searchDTO.getMaxPrice()
                ).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalTime> getAvailableTimeSlots(Long turfId, LocalDate date) {
        Turf turf = turfRepository.findById(turfId)
                .orElseThrow(() -> new RuntimeException("Turf not found with id: " + turfId));

        List<LocalTime> availableSlots = new ArrayList<>();
        LocalTime currentTime = turf.getOperatingStartTime();
        LocalTime endTime = turf.getOperatingEndTime();

        // Generate hourly slots (you can modify this logic based on your requirements)
        while (currentTime.isBefore(endTime)) {
            LocalTime slotEndTime = currentTime.plusHours(1);
            if (slotEndTime.isAfter(endTime)) {
                break;
            }

            if (isTimeSlotAvailable(turfId, date, currentTime, slotEndTime)) {
                availableSlots.add(currentTime);
            }

            currentTime = currentTime.plusHours(1);
        }

        return availableSlots;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTimeSlotAvailable(Long turfId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        Turf turf = turfRepository.findById(turfId)
                .orElseThrow(() -> new RuntimeException("Turf not found with id: " + turfId));

        // Check if time is within operating hours
        if (startTime.isBefore(turf.getOperatingStartTime()) || endTime.isAfter(turf.getOperatingEndTime())) {
            return false;
        }

        // Check for existing bookings
        if (turf.getBookings() != null) {
            for (Booking booking : turf.getBookings()) {
                if (booking.getBookingDate().equals(date)) {
                    // Check if requested time overlaps with existing booking
                    if (!(endTime.isBefore(booking.getSlotStartTime()) || startTime.isAfter(booking.getSlotEndTime()))) {
                        return false;
                    }
                }
            }
        }

        // Check for blocked slots
        if (turf.getBlockedSlots() != null) {
            for (BlockedSlot blockedSlot : turf.getBlockedSlots()) {
                if (blockedSlot.getBlockedDate().equals(date)) {
                    // Check if requested time overlaps with blocked slot
                    if (!(endTime.isBefore(blockedSlot.getStartTime()) || startTime.isAfter(blockedSlot.getEndTime()))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getTurfsOrderedByPopularity() {
        return turfRepository.findTurfsOrderedByBookingCount()
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurfResponseDTO> getAvailableTurfsOnDate(LocalDate date) {
        return turfRepository.findAvailableTurfsOnDate(date)
                .stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalTurfsCount() {
        return turfRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long getTurfsCountBySportType(SportType type) {
        return turfRepository.countByType(type);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTurfsCountByOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Owner not found with id: " + ownerId));
        return turfRepository.countByOwner(owner);
    }

    @Override
    public boolean validateOperatingHours(LocalTime startTime, LocalTime endTime) {
        return startTime != null && endTime != null && startTime.isBefore(endTime);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean turfNameExistsForOwner(String name, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Owner not found with id: " + ownerId));
        return turfRepository.existsByNameAndOwner(name, owner);
    }

    // Helper method to convert Turf entity to basic TurfResponseDTO
    private TurfResponseDTO convertToResponseDTO(Turf turf) {
        return new TurfResponseDTO(
                turf.getId(),
                turf.getName(),
                turf.getPhone(),
                turf.getLocation(),
                turf.getType(),
                turf.getPricePerSlot(),
                turf.getDescription(),
                turf.getOperatingStartTime(),
                turf.getOperatingEndTime(),
                turf.getOwner().getId(),
                turf.getOwner().getName(),
                turf.getOwner().getPhone()
        );
    }

    // Helper method to convert Turf entity to detailed TurfResponseDTO with counts
    private TurfResponseDTO convertToDetailedResponseDTO(Turf turf) {
        TurfResponseDTO responseDTO = new TurfResponseDTO(
                turf.getId(),
                turf.getName(),
                turf.getPhone(),
                turf.getLocation(),
                turf.getType(),
                turf.getPricePerSlot(),
                turf.getDescription(),
                turf.getOperatingStartTime(),
                turf.getOperatingEndTime(),
                turf.getOwner().getId(),
                turf.getOwner().getName(),
                turf.getOwner().getPhone()
        );

        // Set counts (handle null collections)
        responseDTO.setTotalBookings(turf.getBookings() != null ? turf.getBookings().size() : 0);
        responseDTO.setTotalBlockedSlots(turf.getBlockedSlots() != null ? turf.getBlockedSlots().size() : 0);

        return responseDTO;
    }
}
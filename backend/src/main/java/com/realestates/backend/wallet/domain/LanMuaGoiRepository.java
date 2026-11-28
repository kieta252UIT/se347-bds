package com.realestates.backend.wallet.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanMuaGoiRepository {
    Optional<LanMuaGoi> findById(UUID id);
    Optional<LanMuaGoi> findByGiaoDichViId(UUID giaoDichViId);
    List<LanMuaGoi> findByGoiId(UUID goiId);
    LanMuaGoi save(LanMuaGoi lanMua);
}

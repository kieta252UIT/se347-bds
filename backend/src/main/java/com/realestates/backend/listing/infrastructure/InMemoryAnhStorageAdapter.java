package com.realestates.backend.listing.infrastructure;

import com.realestates.backend.auth.exception.AuthProviderUnavailableException;
import com.realestates.backend.listing.application.AnhStoragePort;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.repository.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAnhStorageAdapter implements AnhStoragePort {

    private final ConcurrentMap<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public void luu(String khoaDoiTuong, byte[] noiDung, String mimeType) {
        if (khoaDoiTuong == null || noiDung == null) {
            throw new IllegalArgumentException("Khóa đối tượng và nội dung không được null.");
        }
        storage.put(khoaDoiTuong, noiDung);
    }

    @Override
    public byte[] doc(String khoaDoiTuong) {
        byte[] noiDung = storage.get(khoaDoiTuong);
        if (noiDung == null) {
            throw new AuthProviderUnavailableException("Không tìm thấy tệp ảnh trong kho lưu trữ.");
        }
        return noiDung;
    }

    @Override
    public void xoa(String khoaDoiTuong) {
        if (khoaDoiTuong != null) {
            storage.remove(khoaDoiTuong);
        }
    }

    @Override
    public boolean xoaNeuTonTai(String khoaDoiTuong) {
        return khoaDoiTuong != null && storage.remove(khoaDoiTuong) != null;
    }

    public boolean tonTai(String khoaDoiTuong) {
        return storage.containsKey(khoaDoiTuong);
    }
}


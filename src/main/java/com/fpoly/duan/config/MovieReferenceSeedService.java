package com.fpoly.duan.config;

import com.fpoly.duan.entity.Author;
import com.fpoly.duan.entity.Nation;
import com.fpoly.duan.repository.AuthorRepository;
import com.fpoly.duan.repository.MovieRepository;
import com.fpoly.duan.repository.NationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Chuyển các text tác giả/quốc gia hiện có thành danh mục, không sửa dữ liệu phim cũ. */
@Component
@RequiredArgsConstructor
public class MovieReferenceSeedService implements ApplicationRunner {
    private final MovieRepository movieRepository;
    private final AuthorRepository authorRepository;
    private final NationRepository nationRepository;

    @Override @Transactional
    public void run(ApplicationArguments args) {
        nationRepository.findByNameIgnoreCase("Việt Nam").orElseGet(() -> {
            Nation nation = new Nation(); nation.setName("Việt Nam"); return nationRepository.save(nation);
        });
        movieRepository.findDistinctAuthors().forEach(name -> authorRepository.findByNameIgnoreCase(name.trim()).orElseGet(() -> {
            Author author = new Author(); author.setName(name.trim()); return authorRepository.save(author);
        }));
        movieRepository.findDistinctNations().forEach(name -> nationRepository.findByNameIgnoreCase(name.trim()).orElseGet(() -> {
            Nation nation = new Nation(); nation.setName(name.trim()); return nationRepository.save(nation);
        }));
    }
}

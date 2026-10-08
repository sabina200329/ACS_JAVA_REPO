package com.instakill.feed.domain;

import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class NewestFeedRankingStrategy implements FeedRankingStrategy {

    @Override
    public FeedSort sort() {
        return FeedSort.NEW;
    }

    @Override
    public List<Post> fetch(PostRepository repository, int page, int size) {
        // Apelezi metoda din repository care îți aduce postările noi
        return repository.findLatest(page, size); // Adaptează numele metodei dacă în repository-ul tău se numește altfel
    }
}
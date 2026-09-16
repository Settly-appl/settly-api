package pl.settly.settly_api.suggestions.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.settly.settly_api.suggestions.model.Suggestion;

public interface SuggestionRepository extends JpaRepository<Suggestion, UUID> {

  /**
   * Every suggestion, newest first, with the author joined in — the admin list shows who wrote each
   * one, and a lazy load per row would be an N+1 over the whole table.
   */
  @Query("SELECT s FROM Suggestion s LEFT JOIN FETCH s.user ORDER BY s.createdAt DESC")
  List<Suggestion> findAllNewestFirst();
}

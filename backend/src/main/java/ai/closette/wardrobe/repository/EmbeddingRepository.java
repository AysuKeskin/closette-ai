package ai.closette.wardrobe.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * pgvector access for visual similarity. Vectors are passed as text literals and
 * cast to {@code vector} in SQL, so no extra JDBC type/dialect is needed. The
 * embedding column is deliberately kept out of the JPA entity.
 */
@Repository
public class EmbeddingRepository {

    private final JdbcTemplate jdbc;

    public EmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveWardrobeEmbedding(UUID itemId, float[] vector) {
        jdbc.update(
                "UPDATE wardrobe_items SET embedding = CAST(? AS vector) WHERE id = ?",
                toVectorLiteral(vector), itemId);
    }

    /** Owned wardrobe items most visually similar to the given item (cosine). */
    public List<UUID> similarWardrobe(UUID userId, UUID itemId, int limit) {
        return jdbc.query(
                "SELECT id FROM wardrobe_items "
                        + "WHERE user_id = ? AND id <> ? AND embedding IS NOT NULL "
                        + "AND (SELECT embedding FROM wardrobe_items WHERE id = ?) IS NOT NULL "
                        + "ORDER BY embedding <=> (SELECT embedding FROM wardrobe_items WHERE id = ?) "
                        + "LIMIT ?",
                (rs, i) -> UUID.fromString(rs.getString("id")),
                userId, itemId, itemId, itemId, limit);
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}

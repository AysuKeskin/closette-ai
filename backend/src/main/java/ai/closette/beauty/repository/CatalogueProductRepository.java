package ai.closette.beauty.repository;

import ai.closette.beauty.model.CatalogueProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The shared product cache. Deliberately not user-scoped — see
 * {@link CatalogueProduct} for why.
 */
public interface CatalogueProductRepository extends JpaRepository<CatalogueProduct, String> {

    /** Same shape of match the upstream search gives: the words appear in the name or the brand. */
    @Query("""
            select p from CatalogueProduct p
            where lower(p.productName) like lower(concat('%', :q, '%'))
               or lower(p.brand) like lower(concat('%', :q, '%'))
            """)
    List<CatalogueProduct> search(@Param("q") String query, Pageable page);
}

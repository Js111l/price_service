package com.ecommerce.priceservice.service;


import com.ecommerce.priceservice.entity.Price;
import com.ecommerce.priceservice.entity.Product;
import com.ecommerce.priceservice.exceptions.ErrorKey;
import com.ecommerce.priceservice.exceptions.LogicalException;
import com.ecommerce.priceservice.promotionapplier.PromotionApplierFactory;
import com.ecommerce.priceservice.repository.PriceRepository;
import com.ecommerce.priceservice.repository.ProductRepository;
import com.ecommerce.priceservice.repository.PromotionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class PriceService {

  private final PriceRepository priceRepository;
  private final ProductRepository productRepository;
  private final PromotionRepository promotionRepository;

  public PriceService(PriceRepository priceRepository, ProductRepository productRepository,
      PromotionRepository promotionRepository) {
    this.priceRepository = priceRepository;
    this.productRepository = productRepository;
    this.promotionRepository = promotionRepository;
  }
  public BigDecimal getFinalPrice(List<Long> productIds) {
    var products = productIds.stream().map(id -> this.productRepository.findById(id).orElseThrow(
            () -> new LogicalException(ErrorKey.NOT_FOUND)
    )).toList();

    var finalPrice = new AtomicReference<>(BigDecimal.ZERO);
    final var promotionApplierMap = PromotionApplierFactory.getPromotionApplierMap();

    products.forEach(product -> {
      final var promotions = this.promotionRepository.fetchProductsPromotions(product.getId());
      final var productPrice = getActivePrice(product);
      final var finalProductPrice = new AtomicReference<>(BigDecimal.TEN);

      promotions.forEach(promotion -> {
        var promoApplier = promotionApplierMap.get(promotion.getPromotionType().name());

        final var priceAfterPromotion = promoApplier.applyPromotion(productPrice,
                promotion.getPromotionValue());

        if (priceAfterPromotion.compareTo(finalProductPrice.get()) < 0) {
          finalProductPrice.set(priceAfterPromotion);
        }
      });
      finalPrice.getAndAccumulate(finalProductPrice.get(), BigDecimal::add);
    });
    return finalPrice.get();
  }


  public Price savePrice(Price price) {
    return this.priceRepository.save(price);
  }

  public Price updatePrice(Price price) {
    var priceToUpdate = this.priceRepository.findById(price.getId())
            .orElseThrow(() -> new LogicalException(ErrorKey.NOT_FOUND));
    priceToUpdate.setBigDecimal(price.getBigDecimal());
    priceToUpdate.setProducts(price.getProducts());
    priceToUpdate.setStartDate(price.getStartDate());
    priceToUpdate.setEndDate(price.getEndDate());
    return this.priceRepository.save(priceToUpdate);
  }

  public void deletePrice(Price price) {
    this.priceRepository.delete(price);
  }

  private BigDecimal getActivePrice(Product product) {
    return product.getPrices().stream().findFirst().get().getBigDecimal();
  }

}

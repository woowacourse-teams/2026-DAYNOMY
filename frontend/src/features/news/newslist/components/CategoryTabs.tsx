import type { NewsCategory, NewsCategoryOption } from '../types';

type CategoryTabsProps = {
  categories: NewsCategoryOption[];
  selectedCategory: NewsCategory;
  onChange: (category: NewsCategory) => void;
  ariaLabel?: string;
};

export function CategoryTabs({
  categories,
  selectedCategory,
  onChange,
  ariaLabel = '뉴스 카테고리',
}: CategoryTabsProps) {
  return (
    <nav className="category-tabs" aria-label={ariaLabel}>
      {categories.map((category) => (
        <button
          key={category.value}
          type="button"
          className={category.value === selectedCategory ? 'active' : undefined}
          aria-pressed={category.value === selectedCategory}
          onClick={() => onChange(category.value)}
        >
          {category.label}
        </button>
      ))}
    </nav>
  );
}

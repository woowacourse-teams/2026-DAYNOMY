import type { NewsCategory, NewsCategoryOption } from '../types';

type CategoryTabsProps = {
  categories: NewsCategoryOption[];
  selectedCategory: NewsCategory;
  onChange: (category: NewsCategory) => void;
};

export function CategoryTabs({ categories, selectedCategory, onChange }: CategoryTabsProps) {
  return (
    <nav className="category-tabs" aria-label="뉴스 카테고리">
      {categories.map((category) => (
        <button
          key={category.value}
          type="button"
          className={category.value === selectedCategory ? 'active' : undefined}
          onClick={() => onChange(category.value)}
        >
          {category.label}
        </button>
      ))}
    </nav>
  );
}

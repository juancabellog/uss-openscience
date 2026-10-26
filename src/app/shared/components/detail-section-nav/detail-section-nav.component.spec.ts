import { DetailSectionNavComponent, DETAIL_SECTION_NAV_STRATEGY } from './detail-section-nav.component';

describe('os-detail-section-nav', () => {
  it('keeps section navigation on the current detail route', () => {
    expect(DetailSectionNavComponent.name).toBe('DetailSectionNavComponent');
    expect(DETAIL_SECTION_NAV_STRATEGY.routerLink).toBe('.');
    expect(DETAIL_SECTION_NAV_STRATEGY.routerLink).not.toBe('/');
    expect(DETAIL_SECTION_NAV_STRATEGY.queryParamsHandling).toBe('preserve');
    expect(DETAIL_SECTION_NAV_STRATEGY.replaceUrl).toBe(true);
  });
});

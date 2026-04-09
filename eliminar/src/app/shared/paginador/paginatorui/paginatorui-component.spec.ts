import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaginatoruiComponent } from './paginatorui-component';

describe('PaginatoruiComponent', () => {
  let component: PaginatoruiComponent;
  let fixture: ComponentFixture<PaginatoruiComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaginatoruiComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PaginatoruiComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

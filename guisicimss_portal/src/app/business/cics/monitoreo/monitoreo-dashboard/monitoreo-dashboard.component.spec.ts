import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MonitoreoDashboardComponent } from './monitoreo-dashboard.component';

describe('MonitoreoDashboardComponent', () => {
  let component: MonitoreoDashboardComponent;
  let fixture: ComponentFixture<MonitoreoDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MonitoreoDashboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(MonitoreoDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

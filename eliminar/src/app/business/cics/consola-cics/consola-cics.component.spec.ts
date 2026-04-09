import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsolaCicsComponent } from './consola-cics.component';

describe('ConsolaCicsComponent', () => {
  let component: ConsolaCicsComponent;
  let fixture: ComponentFixture<ConsolaCicsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConsolaCicsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ConsolaCicsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

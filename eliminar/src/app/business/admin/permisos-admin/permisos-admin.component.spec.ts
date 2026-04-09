import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PermisosAdminComponent } from './permisos-admin.component';

describe('PermisosAdminComponent', () => {
  let component: PermisosAdminComponent;
  let fixture: ComponentFixture<PermisosAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PermisosAdminComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PermisosAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});

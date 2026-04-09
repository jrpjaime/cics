import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { Router } from '@angular/router';
import { SharedService } from '../../../shared/services/shared.service';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from '../../../shared/services/alert.service';
import { NAV } from '../../../global/navigation';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  errorMessage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private sharedService: SharedService,
    private router: Router,
    private alertService: AlertService
  ) {
    this.loginForm = this.fb.group({
      user: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    this.alertService.clear();
  }

  login(): void {
    if (this.loginForm.valid) {
      this.errorMessage = null;
      const { user, password } = this.loginForm.value;

      this.authService.login(user, password).subscribe({
        next: (response) => {
          this.sharedService.initializeUserData();
          const roles = this.sharedService.getRolesSync();

          if (!roles.includes('ADMIN')) {
              this.errorMessage = "Acceso denegado: Esta consola es exclusiva para administradores.";
              this.alertService.error(this.errorMessage);
              this.authService.logout();
              return;
          }

          this.router.navigate([NAV.home]);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 401 || err.status === 500) {
            this.errorMessage = 'Verifique su usuario o contraseña.';
          } else {
            this.errorMessage = 'El servidor no responde. Intente más tarde.';
          }
          this.alertService.error(this.errorMessage);
          this.loginForm.get('password')?.reset();
        }
      });
    }
  }
}


import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Observable } from 'rxjs';
import { ModalService } from '../../../services/modal.service';
import { ModalType } from '../../../model/modal-type.model';
import { isObservable } from 'rxjs'; 

@Component({
  selector: 'app-modal', // Este es el selector que usaremos
  standalone: true,
  imports: [CommonModule],
  templateUrl: './modal.component.html',
  styleUrls: ['./modal.component.css']
})
export class ModalComponent implements OnInit {

  // Usamos un observable para conectar directamente con el async pipe en la plantilla
   modalData$!: Observable<ModalType | undefined>;

   isObservable = isObservable;

  constructor(private modalService: ModalService) { }

  ngOnInit(): void {
    this.modalData$ = this.modalService._listener;
  }
}